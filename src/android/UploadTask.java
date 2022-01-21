package com.spoon.backgroundfileupload;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Build;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.sharinpix.SharinPix.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLException;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public final class UploadTask extends Worker {

    private static final boolean DEBUG_SKIP_UPLOAD = false;
    private static final long DELAY_BETWEEN_NOTIFICATION_UPDATE_MS = 200;

    private static final String TAG = "CordovaBackgroundUpload";

    public static final String NOTIFICATION_CHANNEL_ID = "com.spoon.backgroundfileupload.channel";
    public static final String NOTIFICATION_CHANNEL_NAME = "upload channel";

    public static final int MAX_TRIES = 10;

    public static NetworkReceiver networkReceiver = null;
    public static boolean blockRetryNotificationFlag = false;

    // Key stuff
    // <editor-fold>

    // Keys used in the input data
    public static final String KEY_INPUT_ID = "input_id";
    public static final String KEY_INPUT_URL = "input_url";
    public static final String KEY_INPUT_FILEPATH = "input_filepath";
    public static final String KEY_INPUT_FILE_KEY = "input_file_key";
    public static final String KEY_INPUT_HTTP_METHOD = "input_http_method";
    public static final String KEY_INPUT_HEADERS_COUNT = "input_headers_count";
    public static final String KEY_INPUT_HEADERS_NAMES = "input_headers_names";
    public static final String KEY_INPUT_HEADER_VALUE_PREFIX = "input_header_";
    public static final String KEY_INPUT_PARAMETERS_COUNT = "input_parameters_count";
    public static final String KEY_INPUT_PARAMETERS_NAMES = "input_parameters_names";
    public static final String KEY_INPUT_PARAMETER_VALUE_PREFIX = "input_parameter_";
    public static final String KEY_INPUT_NOTIFICATION_TITLE = "input_notification_title";
    public static final String KEY_INPUT_NOTIFICATION_ICON = "input_notification_icon";
    // Input keys but used for configuring the OkHttp instance
    public static final String KEY_INPUT_CONFIG_CONCURRENT_DOWNLOADS = "input_config_concurrent_downloads";
    public static final String KEY_INPUT_CONFIG_INTENT_ACTIVITY = "input_config_intent_activity";


    // Keys used for the progress data
    public static final String KEY_PROGRESS_ID = "progress_id";
    public static final String KEY_PROGRESS_PERCENT = "progress_percent";

    // Keys used for the result
    public static final String KEY_OUTPUT_ID = "output_id";
    public static final String KEY_OUTPUT_IS_ERROR = "output_is_error";
    public static final String KEY_OUTPUT_RESPONSE_FILE = "output_response";
    public static final String KEY_OUTPUT_STATUS_CODE = "output_status_code";
    public static final String KEY_OUTPUT_FAILURE_REASON = "output_failure_reason";
    public static final String KEY_OUTPUT_FAILURE_CANCELED = "output_failure_canceled";
    // </editor-fold>

    public static class Mutex {
        public void acquire() throws InterruptedException { }
        public void release() { }
    }

    // Unified notification
    // <editor-fold>
    public static class UploadForegroundNotification {
        private static final Map<UUID, Float> collectiveProgress = Collections.synchronizedMap(new HashMap<>());
        private static final AtomicLong lastNotificationUpdateMs = new AtomicLong(0);
        private static ForegroundInfo cachedInfo;

        private static final int notificationId = new Random().nextInt();
        public static String notificationTitle = "Default title";
        public static String notificationRetryTitle = "Upload paused";
        public static String notificationRetryText = "Please check your internet connection";
        @IntegerRes
        public static int notificationIconRes = 0;
        public static String notificationIntentActivity;

        private static void configure(final String title, @IntegerRes final int icon, final String intentActivity) {
            notificationTitle = title;
            notificationIconRes = icon;
            notificationIntentActivity = intentActivity;
        }

        private static void progress(final UUID uuid, final float progress) {
            collectiveProgress.put(uuid, progress);
        }

        private static void done(final UUID uuid) {
            collectiveProgress.remove(uuid);
        }

        private static ForegroundInfo getForegroundInfo(final Context context) {
            final long now = System.currentTimeMillis();
            // Set to now to ensure other worker will be throttled
            final long lastUpdate = lastNotificationUpdateMs.getAndSet(now);

            // Throttle, 200ms delay
            if (cachedInfo != null && now - lastUpdate <= DELAY_BETWEEN_NOTIFICATION_UPDATE_MS) {
                // Revert value
                lastNotificationUpdateMs.set(lastUpdate);
                return cachedInfo;
            }

            List<WorkInfo> workInfo;
            try {
                workInfo = WorkManager.getInstance(context)
                        .getWorkInfosByTag(FileTransferBackground.getCurrentTag(context))
                        .get();
            } catch (ExecutionException | InterruptedException e) {
                // Bruh, assume there is no work
                Log.w(TAG, "getForegroundInfo: Problem while retrieving task list:", e);
                workInfo = Collections.emptyList();
            }

            float uploadingProgress = 0f;
            int uploadDone = 0;
            int uploadCount = 0;
            for (WorkInfo info : workInfo) {
                if (!info.getState().isFinished()) {
                    final Float progress = collectiveProgress.get(info.getId());
                    if (progress != null) {
                        uploadingProgress += progress;
                    }
                } else {
                    uploadDone++;
                }
                uploadCount++;
            }

            float totalProgressStore = ((float) uploadDone) / uploadCount;

            // Release lock on retry notification
            blockRetryNotificationFlag = false;

            Log.d(TAG, "eventLabel='getForegroundInfo: general (" + uploadingProgress + ") all (" + collectiveProgress + ")'");

            Class<?> mainActivityClass = null;
            try {
                mainActivityClass = Class.forName(notificationIntentActivity);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            Intent notificationIntent = new Intent(context, mainActivityClass);
            int pendingIntentFlag;
            if (Build.VERSION.SDK_INT >= 23) {
                pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE;
            } else {
                pendingIntentFlag = 0;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, pendingIntentFlag);

            // TODO: click intent open app
            Notification notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(notificationTitle)
                    .setTicker(notificationTitle)
                    .setSmallIcon(notificationIconRes)
                    .setColor(Color.rgb(57, 100, 150))
                    .setOngoing(true)
                    .setProgress(100, (int) (totalProgressStore * 100f), false)
                    .setContentIntent(pendingIntent)
                    .addAction(R.drawable.ic_upload, "Open", pendingIntent)
                    .build();

            notification.flags |= Notification.FLAG_NO_CLEAR;
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;

            cachedInfo = new ForegroundInfo(notificationId, notification);
            return cachedInfo;
        }

        // Foreground notification used to tell user that there is some images left to be uploaded
        public static void getRetryNotification(final Context context) {
            if (!blockRetryNotificationFlag && notificationIntentActivity != null) {
                // Added lock on retry notification
                blockRetryNotificationFlag = true;
                Class<?> mainActivityClass = null;
                try {
                    mainActivityClass = Class.forName(notificationIntentActivity);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                Intent notificationIntent = new Intent(context, mainActivityClass);
                int pendingIntentFlag;
                if (Build.VERSION.SDK_INT >= 23) {
                    pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE;
                } else {
                    pendingIntentFlag = 0;
                }
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, pendingIntentFlag);

                Notification retryNotification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(notificationRetryTitle)
                        .setTicker(notificationRetryTitle)
                        .setContentText(notificationRetryText)
                        .setSmallIcon(notificationIconRes)
                        .setColor(Color.rgb(57, 100, 150))
                        .setContentIntent(pendingIntent)
                        .addAction(R.drawable.ic_upload, "Open", pendingIntent)
                        .build();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.createNotificationChannel(new NotificationChannel(
                            UploadTask.NOTIFICATION_CHANNEL_ID,
                            UploadTask.NOTIFICATION_CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_LOW
                    ));
                    notificationManager.notify(1, retryNotification);
                }
            }
        }
    }
    // </editor-fold>

    private static OkHttpClient httpClient;

    private Call currentCall;

    private static int concurrency = 1;
    private static Semaphore concurrentUploads = new Semaphore(concurrency, true);
    private static Mutex concurrencyLock = new Mutex();

    public UploadTask(@NonNull Context context, @NonNull WorkerParameters workerParams) {

        super(context, workerParams);

        int concurrencyConfig = workerParams.getInputData().getInt(KEY_INPUT_CONFIG_CONCURRENT_DOWNLOADS, 1);

        try {
            concurrencyLock.acquire();
            try {
                if (concurrency != concurrencyConfig) {
                    concurrency = concurrencyConfig;
                    concurrentUploads = new Semaphore(concurrencyConfig, true);
                }
            } finally {
                concurrencyLock.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .cache(null)
                    .build();
        }

        httpClient.dispatcher().setMaxRequests(workerParams.getInputData().getInt(KEY_INPUT_CONFIG_CONCURRENT_DOWNLOADS, 2));

        UploadForegroundNotification.configure(
                workerParams.getInputData().getString(UploadTask.KEY_INPUT_NOTIFICATION_TITLE),
                getApplicationContext().getResources().getIdentifier(workerParams.getInputData().getString(KEY_INPUT_NOTIFICATION_ICON), null, null),
                workerParams.getInputData().getString(UploadTask.KEY_INPUT_CONFIG_INTENT_ACTIVITY)
        );
    }

    @NonNull
    @Override
    public Result doWork() {
        final String id = getInputData().getString(KEY_INPUT_ID);

        if (id == null) {
            Log.e(TAG, "doWork: ID is invalid !");
            return Result.failure();
        }

        // Check retry count
        if (getRunAttemptCount() > MAX_TRIES) {
            return Result.success(new Data.Builder()
                    .putString(KEY_OUTPUT_ID, id)
                    .putBoolean(KEY_OUTPUT_IS_ERROR, true)
                    .putString(KEY_OUTPUT_FAILURE_REASON, "Too many retries")
                    .putBoolean(KEY_OUTPUT_FAILURE_CANCELED, false)
                    .build()
            );
        }

        Request request = null;
        try {
            request = createRequest();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "doWork: File not found !", e);
            return Result.success(new Data.Builder()
                    .putString(KEY_OUTPUT_ID, id)
                    .putBoolean(KEY_OUTPUT_IS_ERROR, true)
                    .putString(KEY_OUTPUT_FAILURE_REASON, "File not found !")
                    .putBoolean(KEY_OUTPUT_FAILURE_CANCELED, false)
                    .build()
            );
        } catch (NullPointerException e) {
            return Result.retry();
        }

        // Register me
        UploadForegroundNotification.progress(getId(), 0f);
        setForegroundAsync(UploadForegroundNotification.getForegroundInfo(getApplicationContext()));

        // Start call
        currentCall = httpClient.newCall(request);

        // Block until call is finished (or cancelled)
        Response response = null;
        try {
            if (!DEBUG_SKIP_UPLOAD) {
                try {
                    try {
                        concurrentUploads.acquire();
                        try {
                            response = currentCall.execute();
                        } catch (SocketTimeoutException e) {
                            e.printStackTrace();
                        } finally {
                            concurrentUploads.release();
                        }
                    } catch (InterruptedException e) {
                        return Result.retry();
                    }
                } catch (SocketException | ProtocolException | SSLException e) {
                    currentCall.cancel();
                    return Result.retry();
                }
            } else {
                for (int i = 0; i < 10; i++) {
                    handleProgress(i * 100, 1000);
                    // Can be interrupted
                    Thread.sleep(200);
                    if (isStopped()) {
                        throw new InterruptedException("Stopped");
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            // If it was user cancelled its ok
            // See #handleProgress for cancel code
            if (isStopped()) {
                final Data data = new Data.Builder()
                        .putString(KEY_OUTPUT_ID, id)
                        .putBoolean(KEY_OUTPUT_IS_ERROR, true)
                        .putString(KEY_OUTPUT_FAILURE_REASON, "User cancelled")
                        .putBoolean(KEY_OUTPUT_FAILURE_CANCELED, true)
                        .build();
                AckDatabase.getInstance(getApplicationContext()).uploadEventDao().insert(new UploadEvent(id, data));
                return Result.success(data);
            } else {
                // But if it was not it must be a connectivity problem or
                // something similar so we retry later
                Log.e(TAG, "doWork: Call failed, retrying later", e);
                return Result.retry();
            }
        } finally {
            // Always remove ourselves from the notification
            UploadForegroundNotification.done(getId());
        }

        // Start building the output data
        final Data.Builder outputData = new Data.Builder()
                .putString(KEY_OUTPUT_ID, id)
                .putBoolean(KEY_OUTPUT_IS_ERROR, false)
                .putInt(KEY_OUTPUT_STATUS_CODE, (!DEBUG_SKIP_UPLOAD) ? response.code() : 200);

        // Try read the response body, if any
        try {
            final String res;
            if (!DEBUG_SKIP_UPLOAD) {
                res = response.body() != null ? response.body().string() : "";
            } else {
                res = "<span>heyo</span>";
            }
            final String filename = "upload-response-" + getId() + ".cached-response";

            try (FileOutputStream fos = getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE)) {
                fos.write(res.getBytes(StandardCharsets.UTF_8));
            }

            outputData.putString(KEY_OUTPUT_RESPONSE_FILE, filename);

        } catch (IOException e) {
            // Should never happen, but if it does it has something to do with reading the response
            Log.e(TAG, "doWork: Error while reading the response body", e);

            // But recover and replace the body with something else
            outputData.putString(KEY_OUTPUT_RESPONSE_FILE, null);
        }

        final Data data = outputData.build();
        AckDatabase.getInstance(getApplicationContext()).uploadEventDao().insert(new UploadEvent(id, data));
        return Result.success(data);
    }

    /**
     * Called internally by the custom request body provider each time 8kio are written.
     */
    private void handleProgress(long bytesWritten, long totalBytes) {
        // The cancel mechanism is best-effort and wont actually halt work, we need to
        // take care of it ourselves.
        if (isStopped()) {
            currentCall.cancel();
            return;
        }

        float percent = (float) bytesWritten / (float) totalBytes;
        UploadForegroundNotification.progress(getId(), percent);

        Log.i(TAG, "handleProgress: " + getId() + " Progress: " + (int) (percent * 100f));

        final Data data = new Data.Builder()
                .putString(KEY_PROGRESS_ID, getInputData().getString(KEY_INPUT_ID))
                .putInt(KEY_PROGRESS_PERCENT, (int) (percent * 100f))
                .build();
        Log.d(TAG, "handleProgress: Progress data: " + data);
        setProgressAsync(data);
        setForegroundAsync(UploadForegroundNotification.getForegroundInfo(getApplicationContext()));
    }

    /**
     * Create the OkHttp request that will be used, already filled with input data.
     *
     * @return A ready to use OkHttp request
     * @throws FileNotFoundException If the file to upload can't be found
     */
    @NonNull
    private Request createRequest() throws FileNotFoundException {
        final String filepath = getInputData().getString(KEY_INPUT_FILEPATH);
        assert filepath != null;
        final String fileKey = getInputData().getString(KEY_INPUT_FILE_KEY);
        assert fileKey != null;

        // Build URL
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(getInputData().getString(KEY_INPUT_URL))).newBuilder().build();

        // Build file reader
        String extension = MimeTypeMap.getFileExtensionFromUrl(filepath);
        MediaType mediaType = MediaType.parse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
        File file = new File(filepath);
        ProgressRequestBody fileRequestBody = new ProgressRequestBody(mediaType, file.length(), new FileInputStream(file), this::handleProgress);

        // Create a BroadcastReceiver to check status of internet connectivity
        if(networkReceiver == null) {
            networkReceiver = new NetworkReceiver();
            IntentFilter networkReceiverIntentFilter = new IntentFilter();
            networkReceiverIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            getApplicationContext().registerReceiver(networkReceiver, networkReceiverIntentFilter);
        }

        // Build body
        final MultipartBody.Builder bodyBuilder = new MultipartBody.Builder();

        // With the parameters
        final int parametersCount = getInputData().getInt(KEY_INPUT_PARAMETERS_COUNT, 0);
        if (parametersCount > 0) {
            final String[] parameterNames = getInputData().getStringArray(KEY_INPUT_PARAMETERS_NAMES);
            assert parameterNames != null;

            for (int i = 0; i < parametersCount; i++) {
                final String key = parameterNames[i];
                final Object value = getInputData().getKeyValueMap().get(KEY_INPUT_PARAMETER_VALUE_PREFIX + i);

                bodyBuilder.addFormDataPart(key, value.toString());
            }
        }

        bodyBuilder.addFormDataPart(fileKey, filepath, fileRequestBody);

        // Start build request
        String method = getInputData().getString(KEY_INPUT_HTTP_METHOD);
        if (method == null) {
            method = "POST";
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .method(method.toUpperCase(), bodyBuilder.build());

        // Write headers
        final int headersCount = getInputData().getInt(KEY_INPUT_HEADERS_COUNT, 0);
        final String[] headerNames = getInputData().getStringArray(KEY_INPUT_HEADERS_NAMES);
        assert headerNames != null;
        for (int i = 0; i < headersCount; i++) {
            final String key = headerNames[i];
            final Object value = getInputData().getKeyValueMap().get(KEY_INPUT_HEADER_VALUE_PREFIX + i);

            requestBuilder.addHeader(key, value.toString());
        }

        // Ok
        return requestBuilder.build();
    }

    /**
     * Custom request body provider that will notify the progress of the read for each 8kio of data
     */
    private static class ProgressRequestBody extends RequestBody {

        @FunctionalInterface
        public interface ProgressListener {
            void onProgress(long bytesWritten, long totalBytes);
        }

        private final MediaType mediaType;
        private final long contentLength;
        private final InputStream stream;
        private final ProgressListener listener;

        private long bytesWritten = 0;
        private long lastProgressTimestamp = 0;

        private ProgressRequestBody(final MediaType mediaType, long contentLength, final InputStream stream, final ProgressListener listener) {
            this.mediaType = mediaType;
            this.contentLength = contentLength;
            this.stream = stream;
            this.listener = listener;
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public void writeTo(@NonNull BufferedSink bufferedSink) throws IOException {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = this.stream.read(buffer)) != -1) {
                bufferedSink.write(buffer, 0, read);

                // Trigger listener
                bytesWritten += read;

                // Event throttling
                long now = System.currentTimeMillis() / 1000;
                if (now - lastProgressTimestamp >= 1) {
                    lastProgressTimestamp = now;
                    listener.onProgress(bytesWritten, contentLength);
                }
            }
        }
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onReceive(Context context, Intent intent) {
            UploadForegroundNotification.done(getId());
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if((connectivityManager == null) || (connectivityManager.getActiveNetworkInfo() == null) || (connectivityManager.getActiveNetworkInfo().isConnectedOrConnecting() == false)) {
                Log.d(TAG, "No internet connection");
                UploadForegroundNotification.getRetryNotification(getApplicationContext());
            }
        }
    }
}
