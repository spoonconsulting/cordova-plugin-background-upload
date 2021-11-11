package com.spoon.backgroundfileupload;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
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
    public static final String KEY_INPUT_CONFIG_CONCURRENT_DOWNLOADS = "input_config_concurrent_downloads";

    public static final String KEY_PROGRESS_ID = "progress_id";
    public static final String KEY_PROGRESS_PERCENT = "progress_percent";

    public static final String KEY_OUTPUT_ID = "output_id";
    public static final String KEY_OUTPUT_IS_ERROR = "output_is_error";
    public static final String KEY_OUTPUT_RESPONSE_FILE = "output_response";
    public static final String KEY_OUTPUT_STATUS_CODE = "output_status_code";
    public static final String KEY_OUTPUT_FAILURE_REASON = "output_failure_reason";
    public static final String KEY_OUTPUT_FAILURE_CANCELED = "output_failure_canceled";

    public static class UploadForegroundNotification extends Service {
        private static final Map<UUID, Float> collectiveProgress = Collections.synchronizedMap(new HashMap<>());
        private static final AtomicLong lastNotificationUpdateMs = new AtomicLong(0);
        private static ForegroundInfo cachedInfo;
        private static ForegroundInfo retryCachedInfo;

        private static final int notificationId = new Random().nextInt();
        public static String notificationTitle = "Default title";
        public static String notificationRetryText = "Some image(s) has not been uploaded(Due to some network issues)";
        @IntegerRes
        public static int notificationIconRes = 0;

        private static void configure(final String title, @IntegerRes final int icon) {
            notificationTitle = title;
            notificationIconRes = icon;
        }

        private static void progress(final UUID uuid, final float progress) {
            collectiveProgress.put(uuid, progress);
        }

        private static void done(final UUID uuid) {
            collectiveProgress.remove(uuid);
        }

        private static ForegroundInfo getForegroundInfo(final Context context) {
            final long now = System.currentTimeMillis();
            final long lastUpdate = lastNotificationUpdateMs.getAndSet(now);

            if (cachedInfo != null && now - lastUpdate <= DELAY_BETWEEN_NOTIFICATION_UPDATE_MS) {
                lastNotificationUpdateMs.set(lastUpdate);
                return cachedInfo;
            }

            List<WorkInfo> workInfo;
            try {
                workInfo = WorkManager.getInstance(context)
                        .getWorkInfosByTag(FileTransferBackground.WORK_TAG_UPLOAD)
                        .get();
            } catch (ExecutionException | InterruptedException e) {
                Log.w(TAG, "getForegroundInfo: Problem while retrieving task list:", e);
                workInfo = Collections.emptyList();
            }

            float totalProgress = 0f;
            int uploadCount = 0;
            for (WorkInfo info : workInfo) {
                if (!info.getState().isFinished()) {
                    final Float progress = collectiveProgress.get(info.getId());
                    if (progress != null) {
                        totalProgress += progress;
                    }
                    uploadCount++;
                }
            }

            totalProgress /= (float) uploadCount;

            Log.d(TAG, "eventLabel='getForegroundInfo: general (" + totalProgress + ") all (" + collectiveProgress + ")'");

            Notification notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(notificationTitle)
                    .setTicker(notificationTitle)
                    .setSmallIcon(notificationIconRes)
                    .setColor(Color.rgb(57, 100, 150))
                    // Notify device that the notification is for an ongoing process
                    .setOngoing(true)
                    .setProgress(100, (int) (totalProgress * 100f), false)
                    .build();

            // Not allow user to clear
            notification.flags |= Notification.FLAG_NO_CLEAR;
            // Notify device that the notification is for an ongoing process
            // Some device support this flag
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            // Notify device that the notification is for a currently running foreground service
            notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;

            cachedInfo = new ForegroundInfo(notificationId, notification);
            return cachedInfo;
        }

        // Foreground notification used to tell user that there is some images left to be uploaded
        public static ForegroundInfo getRetryForegroundInfo(final Context context) {
            Notification retryNotification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(notificationTitle)
                    .setTicker(notificationTitle)
                    .setContentText(notificationRetryText)
                    .setSmallIcon(notificationIconRes)
                    .setColor(Color.rgb(57, 100, 150))
                    .build();

            retryCachedInfo = new ForegroundInfo(notificationId, retryNotification);
            return retryCachedInfo;
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    private static OkHttpClient httpClient;

    private Call currentCall;

    public UploadTask(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

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

            httpClient.dispatcher().setMaxRequests(workerParams.getInputData().getInt(KEY_INPUT_CONFIG_CONCURRENT_DOWNLOADS, 2));
        }

        UploadForegroundNotification.configure(
                workerParams.getInputData().getString(UploadTask.KEY_INPUT_NOTIFICATION_TITLE),
                getApplicationContext().getResources().getIdentifier(workerParams.getInputData().getString(KEY_INPUT_NOTIFICATION_ICON), null, null)
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
            setForegroundAsync(UploadForegroundNotification.getRetryForegroundInfo(getApplicationContext()));
            return Result.retry();
        }

        UploadForegroundNotification.progress(getId(), 0f);
        setForegroundAsync(UploadForegroundNotification.getForegroundInfo(getApplicationContext()));

        currentCall = httpClient.newCall(request);

        Response response;
        try {
            if (!DEBUG_SKIP_UPLOAD) {
                try {
                    response = currentCall.execute();
                } catch (SocketException | ProtocolException | SSLException e) {
                    currentCall.cancel();
                    setForegroundAsync(UploadForegroundNotification.getRetryForegroundInfo(getApplicationContext()));
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
                Log.e(TAG, "doWork: Call failed, retrying later", e);
                setForegroundAsync(UploadForegroundNotification.getRetryForegroundInfo(getApplicationContext()));
                return Result.retry();
            }
        } finally {
            UploadForegroundNotification.done(getId());
        }

        final Data.Builder outputData = new Data.Builder()
                .putString(KEY_OUTPUT_ID, id)
                .putBoolean(KEY_OUTPUT_IS_ERROR, false)
                .putInt(KEY_OUTPUT_STATUS_CODE, (!DEBUG_SKIP_UPLOAD) ? response.code() : 200);

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
            Log.e(TAG, "doWork: Error while reading the response body", e);

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

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(getInputData().getString(KEY_INPUT_URL))).newBuilder().build();

        String extension = MimeTypeMap.getFileExtensionFromUrl(filepath);
        MediaType mediaType = MediaType.parse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
        File file = new File(filepath);
        ProgressRequestBody fileRequestBody = new ProgressRequestBody(mediaType, file.length(), new FileInputStream(file), this::handleProgress);

        //Create a BroadcastReceiver to check status of internet connectivity
        NetworkReceiver networkReceiver = new NetworkReceiver();
        IntentFilter networkReceiverIntentFilter = new IntentFilter();
        networkReceiverIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        getApplicationContext().registerReceiver(networkReceiver, networkReceiverIntentFilter);

        final MultipartBody.Builder bodyBuilder = new MultipartBody.Builder();

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

        String method = getInputData().getString(KEY_INPUT_HTTP_METHOD);
        if (method == null) {
            method = "POST";
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .method(method.toUpperCase(), bodyBuilder.build());

        final int headersCount = getInputData().getInt(KEY_INPUT_HEADERS_COUNT, 0);
        final String[] headerNames = getInputData().getStringArray(KEY_INPUT_HEADERS_NAMES);
        assert headerNames != null;
        for (int i = 0; i < headersCount; i++) {
            final String key = headerNames[i];
            final Object value = getInputData().getKeyValueMap().get(KEY_INPUT_HEADER_VALUE_PREFIX + i);

            requestBuilder.addHeader(key, value.toString());
        }

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

                bytesWritten += read;

                long now = System.currentTimeMillis() / 1000;
                if (now - lastProgressTimestamp >= 1) {
                    lastProgressTimestamp = now;
                    listener.onProgress(bytesWritten, contentLength);
                }
            }
        }
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if((connectivityManager == null) || (connectivityManager.getActiveNetworkInfo() == null) || (connectivityManager.getActiveNetworkInfo().isConnected() == false)) {
                Log.d(TAG, "WIFI is not connected");
                setForegroundAsync(UploadForegroundNotification.getRetryForegroundInfo(getApplicationContext()));
            }
        }
    }
}
