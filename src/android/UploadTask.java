package com.spoon.backgroundfileupload;

import android.app.Notification;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.ionic.starter.R;
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

    private static final String TAG = "UploadWorker";

    public static final String NOTIFICATION_CHANNEL_ID = "com.spoon.backgroundfileupload.channel";
    public static final String NOTIFICATION_CHANNEL_NAME = "upload channel";

    // Key stuff
    // <editor-fold>

    // Keys used in the input data
    public static final String KEY_INPUT_ID = "input_id";
    public static final String KEY_INPUT_URL = "input_url";
    public static final String KEY_INPUT_FILEPATH = "input_filepath";
    public static final String KEY_INPUT_FILE_KEY = "input_file_key";
    public static final String KEY_INPUT_HEADERS_COUNT = "input_headers_count";
    public static final String KEY_INPUT_HEADERS_NAMES = "input_headers_names";
    public static final String KEY_INPUT_HEADER_VALUE_PREFIX = "input_header_";
    public static final String KEY_INPUT_PARAMETERS_COUNT = "input_parameters_count";
    public static final String KEY_INPUT_PARAMETERS_NAMES = "input_parameters_names";
    public static final String KEY_INPUT_PARAMETER_VALUE_PREFIX = "input_parameter_";
    public static final String KEY_INPUT_NOTIFICATION_TITLE = "input_notification_title";

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

    private static OkHttpClient httpClient;

    private Call currentCall;

    public UploadTask(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        if (httpClient == null) {
            // TODO: make this configurable somewhere else
            httpClient = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .cache(null)
                    .build();

            httpClient.dispatcher().setMaxRequests(2);
        }
    }

    private long getLowQualityId() {
        return getId().getLeastSignificantBits();
    }

    @NonNull
    @Override
    public Result doWork() {
        final String id = getInputData().getString(KEY_INPUT_ID);

        if (id == null) {
            Log.e(TAG, "doWork: ID is invalid !");
            return Result.failure();
        }

        Request request;
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
        }

        setForegroundAsync(createForegroundInfo(0));

        // Start call
        currentCall = httpClient.newCall(request);

        // Block until call is finished (or cancelled)
        Response response;
        try {
            response = currentCall.execute();
        } catch (IOException e) {
            // If it was user cancelled its ok
            if (isStopped()) {
                return Result.success(new Data.Builder()
                        .putString(KEY_OUTPUT_ID, id)
                        .putBoolean(KEY_OUTPUT_IS_ERROR, true)
                        .putString(KEY_OUTPUT_FAILURE_REASON, "User cancelled")
                        .putBoolean(KEY_OUTPUT_FAILURE_CANCELED, true)
                        .build()
                );
            } else {
                // But if it was not it must be a connectivity problem or
                // something similar so we retry later
                Log.e(TAG, "doWork: Call failed, retrying later", e);
                return Result.retry();
            }
        }

        // Start building the output data
        final Data.Builder outputData = new Data.Builder()
                .putString(KEY_OUTPUT_ID, id)
                .putBoolean(KEY_OUTPUT_IS_ERROR, false)
                .putInt(KEY_OUTPUT_STATUS_CODE, response.code());

        // Try read the response body, if any
        try {
            final String res = response.body() != null ? response.body().string() : "";
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

        return Result.success(outputData.build());
    }

    /**
     * Called internally by the custom request body provider each time 8kio are written.
     */
    private void handleProgress(long bytesWritten, long totalBytes) {
        if (isStopped()) {
            currentCall.cancel();
            return;
        }

        double percent = ((double) bytesWritten / (double) totalBytes) * 100.0;
        Log.i(TAG, "handleProgress: " + getId() + " Progress: " + (int) percent);

        setProgressAsync(new Data.Builder()
                .putString(KEY_PROGRESS_ID, getInputData().getString(KEY_INPUT_ID))
                .putInt(KEY_PROGRESS_PERCENT, (int) percent)
                .build()
        );
        setForegroundAsync(createForegroundInfo((int) percent));
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

        // Build URL ...
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(getInputData().getString(KEY_INPUT_URL))).newBuilder();

        // ... with parameters if any
        final int parametersCount = getInputData().getInt(KEY_INPUT_PARAMETERS_COUNT, 0);
        if (parametersCount > 0) {
            final String[] parameterNames = getInputData().getStringArray(KEY_INPUT_PARAMETERS_NAMES);
            assert parameterNames != null;

            for (int i = 0; i < parametersCount; i++) {
                final String key = parameterNames[i];
                final Object value = getInputData().getKeyValueMap().get(KEY_INPUT_PARAMETER_VALUE_PREFIX + i);

                urlBuilder.addQueryParameter(key, value.toString());
            }
        }

        // Build file reader
        String extension = MimeTypeMap.getFileExtensionFromUrl(filepath);
        MediaType mediaType = MediaType.parse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
        File file = new File(filepath);
        ProgressRequestBody fileRequestBody = new ProgressRequestBody(mediaType, file.length(), new FileInputStream(file), this::handleProgress);

        // Build body
        MultipartBody body = new MultipartBody.Builder()
                .addFormDataPart(fileKey, filepath, fileRequestBody)
                .build();

        // Start build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.build())
                .post(body);

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

    @NonNull
    private ForegroundInfo createForegroundInfo(int progress) {
        final String title = getInputData().getString(KEY_INPUT_NOTIFICATION_TITLE);

        // TODO: click intent open app
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setTicker(title)
                .setSmallIcon(R.drawable.ic_upload)
                .setColor(Color.rgb(57, 100, 150))
                .setOngoing(true)
                .setProgress(100, progress, false)
                .build();

        return new ForegroundInfo((int) getLowQualityId(), notification);
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
                listener.onProgress(bytesWritten, contentLength);
            }
        }
    }
}
