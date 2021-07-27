package com.spoon.backgroundfileupload;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class PendingAcknowledgeTask extends Worker {

    private static final String TAG = "PendingAcknowledgeTask";

    public PendingAcknowledgeTask(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // The purpose of this task is to exist and retry until it is cancelled.
        // So it will retransmit the data from the upload and immediately terminate
        // waiting to be retried some time later.

        setProgressAsync(getInputData());

        try {
            // If we don't sleep a bit, the setProgressAsync won't have time to trigger something on the other
            // side and nothing will be resent.
            // NOTE: May need to be tuned.
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // That's actually ok, it means we've been cancelled
            return Result.success(getInputData());
        }

        if (isStopped()) {
            return Result.success(getInputData());
        } else {
            return Result.retry();
        }
    }
}
