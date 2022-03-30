package com.spoon.backgroundfileupload;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class ManagerService extends Worker {
    private static final String TAG = "ManagerService";

    public ManagerService(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        PendingUpload nextPendingUpload = AckDatabase.getInstance(getApplicationContext()).pendingUploadDao().getLastEntry();

        startUpload(nextPendingUpload.getId(), nextPendingUpload.getOutputData());

        Log.d("ZAFIR", nextPendingUpload.getOutputData().toString());

        AckDatabase.getInstance(getApplicationContext()).pendingUploadDao().delete((nextPendingUpload.getId()));

        return Result.retry();
    }

    private void startUpload(final String uploadId, final Data payload) {
        Log.d(TAG, "startUpload: Starting work via work manager");

        OneTimeWorkRequest.Builder workRequestBuilder = new OneTimeWorkRequest.Builder(UploadTask.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .keepResultsForAtLeast(0, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .addTag(FileTransferBackground.WORK_TAG_UPLOAD)
                .addTag(FileTransferBackground.getCurrentTag(getApplicationContext()))
                .setInputData(payload);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            workRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        }

        OneTimeWorkRequest workRequest = workRequestBuilder.build();

        WorkManager.getInstance(getApplicationContext())
                .enqueueUniqueWork(uploadId, ExistingWorkPolicy.APPEND, workRequest);

        Log.d(TAG,"eventLabel='Uploader starting upload' uploadId='" + uploadId + "'");
    }
}
