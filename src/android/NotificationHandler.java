package com.spoon.backgroundfileupload;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.task.UploadTaskObserver;

import org.jetbrains.annotations.NotNull;

public class NotificationHandler implements UploadTaskObserver {
    NotificationCompat.Builder notification;

    private int mCurrent;
    private UploadService mService;
    private NotificationManager mNotificationManager;

    private int uploaded = 0;

    public NotificationHandler(UploadService service, int current) {
        this.mService = service;
        this.mCurrent = current;
        this.mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void notify(String uploadId, int notificationId, Notification notificatior) {
        if (mService.holdForegroundNotification(uploadId, notificatior)) {
            mNotificationManager.cancel(notificationId);
        } else {
            mNotificationManager.notify(notificationId, notificatior);
        }
    }

    @Override
    public void onCompleted(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig) {
    }

    @Override
    public void onError(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig, @NotNull Throwable throwable) {
        notification.setContentText("Uploads failed");
        notification.setProgress(0, 0, false);

        notify(uploadInfo.getUploadId(), 1, notification.build());
    }

    @Override
    public void onProgress(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig) {
        notification.setContentText(uploadInfo.getProgressPercent() + "%");
        notification.setProgress(100, uploadInfo.getProgressPercent(), false);
        notify(uploadInfo.getUploadId(), 1, notification.build());
    }

    @Override
    public void onStart(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                mNotificationManager.getNotificationChannel(uploadNotificationConfig.getNotificationChannelId());
            } catch (IllegalArgumentException e) {
                throw e;
            }
        }

        notification = new NotificationCompat.Builder(mService, uploadNotificationConfig.getNotificationChannelId())
                .setContentTitle(String.format("%s/%s file(s) uploaded", uploaded, mCurrent))
                .setContentText(String.format("Upload of %s", uploadInfo.getUploadId().toUpperCase()))
                .setProgress(100, 100 / mCurrent, false)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(false);

        notify(uploadInfo.getUploadId(), 1, notification.build());
    }

    @Override
    public void onSuccess(@NotNull UploadInfo uploadInfo, int i, @NotNull UploadNotificationConfig uploadNotificationConfig, @NotNull ServerResponse serverResponse) {
        mNotificationManager.cancel(i);

        notification.setContentTitle(String.format("%s/%s file(s) uploaded", ++uploaded, mCurrent));

        if (uploaded == mCurrent) {
            notification.setContentText("Done");
            notification.setProgress(0, 0, false);
            mNotificationManager.notify(1, notification.build());
        } else {
            notification.setProgress(0, 0, false);
            mNotificationManager.notify(1, notification.build());
        }
    }
}
