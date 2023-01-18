package com.spoon.backgroundfileupload;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UploadTask extends Worker {

    private static final boolean DEBUG_SKIP_UPLOAD = false;
    public static final long DELAY_BETWEEN_NOTIFICATION_UPDATE_MS = 200;

    public static final String TAG = "CordovaBackgroundUpload";

    public static final String NOTIFICATION_CHANNEL_ID = "com.spoon.backgroundfileupload.channel";
    public static final String NOTIFICATION_CHANNEL_NAME = "upload channel";

    public static final int MAX_TRIES = 10;

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

    private static UploadNotification uploadNotification = null;
    private static UploadForegroundNotification uploadForegroundNotification = null;

    public static class Mutex {
        public void acquire() throws InterruptedException { }
        public void release() { }
    }

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            UploadForegroundNotification.configure(
                    workerParams.getInputData().getString(UploadTask.KEY_INPUT_NOTIFICATION_TITLE),
                    getApplicationContext().getResources().getIdentifier(workerParams.getInputData().getString(KEY_INPUT_NOTIFICATION_ICON), null, null),
                    workerParams.getInputData().getString(UploadTask.KEY_INPUT_CONFIG_INTENT_ACTIVITY)
            );
            uploadForegroundNotification = new UploadForegroundNotification();
        } else {
            UploadNotification.configure(
                    workerParams.getInputData().getString(UploadTask.KEY_INPUT_NOTIFICATION_TITLE),
                    getApplicationContext().getResources().getIdentifier(workerParams.getInputData().getString(KEY_INPUT_NOTIFICATION_ICON), null, null),
                    workerParams.getInputData().getString(UploadTask.KEY_INPUT_CONFIG_INTENT_ACTIVITY)
            );
            uploadNotification = new UploadNotification(getApplicationContext());
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        if(!hasNetworkConnection()) {
            return Result.retry();
        }

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
        uploadForegroundNotification.progress(getId(), 0f);
        handleNotification();

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
                            return Result.retry();
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
            uploadForegroundNotification.done(getId());
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
        handleNotification();
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
        MediaType mediaType;
        if (extension.equals("json") || extension.equals("json")) {
            // Does not support devices less than Android 10 (Stop Execution)
            // https://stackoverflow.com/questions/44667125/getmimetypefromextension-returns-null-when-i-pass-json-as-extension
            mediaType = MediaType.parse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) + "; charset=utf-8");
        } else {
            mediaType = MediaType.parse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
        }
        File file = new File(filepath);
        ProgressRequestBody fileRequestBody = new ProgressRequestBody(mediaType, file.length(), new FileInputStream(file), this::handleProgress);

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
        bodyBuilder.setType(MultipartBody.FORM);

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

    private void handleNotification() {
        Log.d(TAG, "Upload Notification");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setForegroundAsync(uploadForegroundNotification.getForegroundInfo(getApplicationContext()));
        } else  {
            uploadNotification.updateProgress();
        }
        Log.d(TAG, "Upload Notification Exit");
    }

    private synchronized boolean hasNetworkConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if((connectivityManager == null) || (connectivityManager.getActiveNetworkInfo() == null) || (connectivityManager.getActiveNetworkInfo().isConnectedOrConnecting() == false)) {
            Log.d(TAG, "No internet connection");
            return false;
        }
        return true;
    }
}
