package com.spoon.backgroundfileupload;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.UploadServiceConfig;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.exceptions.UserCancelledUploadException;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.task.UploadTaskObserver;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Nick Adna on 3/28/2020.
 */

public abstract class AbstractSingleNotificationHandler implements UploadTaskObserver {

    private UploadService mService;
    private String mNotificationChannelId;
    private ConcurrentHashMap<String, TaskData> mTasks = new ConcurrentHashMap<>();
    private NotificationManager mNotificationManager;

    public enum TaskStatus {
        InProgress,
        Succedeed,
        Failed,
        Cancelled
    }

    public AbstractSingleNotificationHandler(UploadService mService, String mNotificationChannelId) {
        this.mService = mService;
        this.mNotificationChannelId = mNotificationChannelId;

        mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                mNotificationManager.getNotificationChannel(mNotificationChannelId);
            } catch (IllegalArgumentException e) {
                throw e;
            }
        }
    }

    public void removeTask(String uploadId) {
        mTasks.remove(uploadId);
    }

    public abstract NotificationCompat.Builder updateNotification(
            NotificationManager notificationManager,
            NotificationCompat.Builder notificationBuilder,
            Map<String, TaskData> tasks
    );

    private void updateTask(TaskStatus status, UploadInfo info, UploadNotificationConfig config) {
        mTasks.put(info.getUploadId(), new TaskData(status, info, config));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService, mNotificationChannelId);
        Notification notification = updateNotification(mNotificationManager, builder, mTasks)
                .setGroup(UploadServiceConfig.getNamespace())
                .setOngoing(true)
                .build();

        mService.holdForegroundNotification(AbstractSingleNotificationHandler.class.getName(), notification);
    }

    @Override
    public void onCompleted(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig) {

    }

    @Override
    public void onError(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig, @NotNull Throwable throwable) {
        if (throwable instanceof UserCancelledUploadException) {
            updateTask(TaskStatus.Cancelled, uploadInfo, uploadNotificationConfig);
        } else {
            updateTask(TaskStatus.Failed, uploadInfo, uploadNotificationConfig);
        }
    }

    @Override
    public void onProgress(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig) {
        updateTask(TaskStatus.InProgress, uploadInfo, uploadNotificationConfig);
    }

    @Override
    public void onStart(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig) {
        updateTask(TaskStatus.InProgress, uploadInfo, uploadNotificationConfig);
    }

    @Override
    public void onSuccess(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig, @NotNull ServerResponse serverResponse) {
        updateTask(TaskStatus.Succedeed, uploadInfo, uploadNotificationConfig);
    }
}