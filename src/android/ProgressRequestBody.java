package com.spoon.backgroundfileupload;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class ProgressRequestBody extends RequestBody {

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

    public ProgressRequestBody(final MediaType mediaType, long contentLength, final InputStream stream, final ProgressListener listener) {
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
