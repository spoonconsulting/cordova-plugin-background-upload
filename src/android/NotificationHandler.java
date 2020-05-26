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
    private String defaultTitle;
    private String defaultContent;

    public NotificationHandler(@NotNull UploadService service, Activity context, String defaultTitle, String defaultContent) {
        super(service);
        this.mContext = context;
        this.defaultTitle = defaultTitle;
        this.defaultContent = defaultContent;
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
                inProgress == 0 ? defaultTitle : String.format("%s upload(s) remaining", uploadCount)
        );

        notificationLayout.setTextViewText(
                resources.getIdentifier("notification_content_left", idDef, pkg),
                inProgress == 0 ? defaultContent : String.format("%d in progress", inProgress)
        );

        notificationLayout.setTextViewText(
                resources.getIdentifier("notification_content_right", idDef, pkg),
                inProgress == 0 ? "" : toReadable(speed)
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

        float value = 0;

        if (unit == BPS) {
            value = speed / 1000f;
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
        final String BPS = "bps";
        final String KBPS = "kbps";
        final String MBPS = "Mbps";

        if (speed >= 1000) {
            return String.format("%.0f %s", speed / 1000, MBPS);
        }

        if (speed < 1) {
            return String.format("%.0f %s", speed * 1000, BPS);
        }

        return String.format("%.0f %s", speed, KBPS);
    }
}
