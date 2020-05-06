package com.spoon.backgroundfileupload;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.observer.task.AbstractSingleNotificationHandler;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class NotificationHandler extends AbstractSingleNotificationHandler {

    private Activity mContext;
    private long uploadCount = 0;

    public NotificationHandler(@NotNull UploadService service, Activity context) {
        super(service);
        this.mContext = context;
    }

    @Override
    public void onStart(@NotNull UploadInfo info, int notificationId, @NotNull UploadNotificationConfig notificationConfig) {
        super.onStart(info, notificationId, notificationConfig);
        this.uploadCount = PendingUpload.count(PendingUpload.class);
    }

    @Override
    public void onCompleted(@NotNull UploadInfo info, int notificationId, @NotNull UploadNotificationConfig notificationConfig) {
        super.onCompleted(info, notificationId, notificationConfig);
        removeTask(info.getUploadId());
        this.uploadCount = PendingUpload.count(PendingUpload.class);
    }

    @Nullable
    @Override
    public NotificationCompat.Builder updateNotification(@NotNull NotificationManager notificationManager, @NotNull NotificationCompat.Builder builder, @NotNull Map<String, TaskData> map) {
        int speed = 0;
        int inProgress = 0;

        for (Map.Entry<String, TaskData> entry : map.entrySet()) {
            if (entry.getValue().getStatus() == TaskStatus.InProgress) {
                inProgress++;

                speed += convertUnit(
                        entry.getValue().getInfo().getUploadRate().getUnit().name(),
                        entry.getValue().getInfo().getUploadRate().getValue()
                );
            }
        }

        String pkg = mContext.getApplication().getPackageName();
        String layoutDef = "layout";
        String idDef = "id";

        Resources resources = mContext.getResources();

        RemoteViews notificationLayout = new RemoteViews(mContext.getPackageName(),
                resources.getIdentifier("notification_small", layoutDef, pkg));

        notificationLayout.setTextViewText(
                resources.getIdentifier("notification_title", idDef, pkg),
                String.format("%s uploads remaining", uploadCount)
        );

        notificationLayout.setTextViewText(
                resources.getIdentifier("notification_content_left", idDef, pkg),
                String.format("%d in progress", inProgress)
        );

        notificationLayout.setTextViewText(
                resources.getIdentifier("notification_content_right", idDef, pkg),
                getUploadRate(speed)
        );

        return builder
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayout)
                .setSmallIcon(android.R.drawable.ic_menu_upload);
    }

    private float convertUnit(String unit, int speed) {
        final String KPS = "KilobitPerSecond";
        final String MPS = "MegabitPerSecond";

        int value = 0;

        if (unit == KPS) {
            value = speed;
        }

        if (unit == MPS) {
            value = speed * 1000;
        }

        return value;
    }

    private String getUploadRate(int speed) {
        final String KPS = "Ko/s";
        final String MPS = "Mo/s";

        String value = "";

        int length = (int) (Math.log10(speed) + 1);

        if (length >= 4) {
            value = (speed / 1000) + MPS;
        } else {
            if (speed != 0) {
                value = speed + KPS;
            }
        }

        return value;
    }
}
