package com.spoon.backgroundfileupload;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import net.gotev.uploadservice.UploadService;

import java.util.Map;

/**
 * Created by Nick Adna on 3/28/2020.
 */

public class NotificationHandler extends AbstractSingleNotificationHandler {

    private Activity mContext;

    public NotificationHandler(Activity context, UploadService mService, String mNotificationChannelId) {
        super(mService, mNotificationChannelId);
        this.mContext = context;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public NotificationCompat.Builder updateNotification(NotificationManager notificationManager, NotificationCompat.Builder notificationBuilder, Map<String, TaskData> tasks) {
        int speed = 0;
        int inProgress = 0;
        int failed = 0;

        for (Map.Entry<String, TaskData> entry : tasks.entrySet()) {
            String uploadId = entry.getValue().getInfo().getUploadId();

            if (entry.getValue().getStatus() == TaskStatus.InProgress) {
                inProgress++;

                speed += convertUnit(
                        entry.getValue().getInfo().getUploadRate().getUnit().name(),
                        entry.getValue().getInfo().getUploadRate().getValue()
                );
            }

            if (entry.getValue().getStatus() == TaskStatus.Failed) {
                failed++;
            }

            if (entry.getValue().getStatus() != TaskStatus.InProgress) {
                removeTask(uploadId);
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
                String.format("%s uploads remaining", PendingUpload.all().size())
        );

        notificationLayout.setTextViewText(
                resources.getIdentifier("notification_content_left", idDef, pkg),
                String.format("%d in progress - %d failed", inProgress, failed)
        );

        notificationLayout.setTextViewText(
                resources.getIdentifier("notification_content_right", idDef, pkg),
                getUploadRate(speed / inProgress)
        );

        return notificationBuilder
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

        String value = "Init uploads";

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