package com.spoon.backgroundfileupload;

import android.app.NotificationManager;
import android.app.PendingIntent;

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

    private float speed = 0;
    private int inProgress = 0;
    private PendingIntent mPendingIntent;

    public NotificationHandler(@NotNull UploadService service, PendingIntent pendingIntent) {
        super(service);
        this.mPendingIntent = pendingIntent;
    }

    @Override
    public void onCompleted(@NotNull UploadInfo info, int notificationId, @NotNull UploadNotificationConfig notificationConfig) {
        super.onCompleted(info, notificationId, notificationConfig);
        removeTask(info.getUploadId());
    }

    @Nullable
    @Override
    public NotificationCompat.Builder updateNotification(@NotNull NotificationManager notificationManager, @NotNull NotificationCompat.Builder builder, @NotNull Map<String, TaskData> tasks) {
        this.speed = 0;
        this.inProgress = 0;

        for (Map.Entry<String, TaskData> entry : tasks.entrySet()) {
            if (entry.getValue().getStatus() == TaskStatus.InProgress) {
                inProgress++;
                speed += convertUnitToKbps(
                        entry.getValue().getInfo().getUploadRate().getUnit().name(),
                        entry.getValue().getInfo().getUploadRate().getValue()
                );
            }
        }

        return builder
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentTitle(String.format("Uploading %d at %s", inProgress, toReadable(speed)))
                .setContentIntent(mPendingIntent);
    }

    private float convertUnitToKbps(String unit, int speed) {
        final String BPS = UploadRate.UploadRateUnit.BitPerSecond.name();
        final String KPS = UploadRate.UploadRateUnit.KilobitPerSecond.name();
        final String MPS = UploadRate.UploadRateUnit.MegabitPerSecond.name();

        float value = 0;
        if (unit == BPS) {
            value = speed / 8000f;
        }
        if (unit == KPS) {
            value = speed / 8;
        }
        if (unit == MPS) {
            value = speed * 125;
        }

        return value;
    }

    private String toReadable(float speed) {
        final String BPS = "B/s";
        final String KBPS = "kB/s";
        final String MBPS = "MB/s";

        if (speed >= 1000) {
            return String.format("%.0f %s", speed / 1000, MBPS);
        }
        if (speed < 1) {
            return String.format("%.0f %s", speed * 1000, BPS);
        }

        return String.format("%.0f %s", speed, KBPS);
    }
}
