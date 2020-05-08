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
import net.gotev.uploadservice.data.UploadRate;
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
    public NotificationCompat.Builder updateNotification(@NotNull NotificationManager notificationManager, @NotNull NotificationCompat.Builder builder, @NotNull Map<String, TaskData> tasks) {
        float speed = 0;
        int inProgress = 0;

        for (Map.Entry<String, TaskData> entry : tasks.entrySet()) {
            if (entry.getValue().getStatus() == TaskStatus.InProgress) {
                inProgress++;

                speed += convertUnitToKbps(
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
                toReadable(speed)
        );

        return builder
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayout)
                .setSmallIcon(android.R.drawable.ic_menu_upload);
    }

    private float convertUnitToKbps(String unit, int speed) {
        final String BPS = UploadRate.UploadRateUnit.BitPerSecond.name();
        final String KPS = UploadRate.UploadRateUnit.KilobitPerSecond.name();
        final String MPS = UploadRate.UploadRateUnit.MegabitPerSecond.name();

        int value = 0;

        if (unit == BPS) {
            value = speed / 1000;
        }

        if (unit == KPS) {
            value = speed;
        }

        if (unit == MPS) {
            value = speed * 1000;
        }

        return value;
    }

    private String toReadable(float speed) {
        final String KPS = "Kbps";
        final String MPS = "Mbps";

        String value = "";

        int length = (int) (Math.log10(speed) + 1);

        if (length >= 4) {
            float tmpSpeed = speed / 1000;
            value = String.format("%.1f %s", tmpSpeed, MPS);
        }

        if ((length >= 1 && length < 4) && speed != 0) {
            value = String.format("%s %s", (int) speed, KPS);
        }

        if (speed > 0f && speed < 1f) {
            value = String.format("%s %s", speed, KPS);
        }

        return value;
    }
}
