package com.spoon.backgroundfileupload;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.IntegerRes;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.util.Random;

public class UploadNotification {
    private Context context;

    private static final int notificationId = new Random().nextInt();
    public static String notificationTitle = "Default title";
    public static String notificationRetryText = "Please check your internet connection";
    @IntegerRes
    public static int notificationIconRes = 0;
    public static String notificationIntentActivity;

    public static NotificationManager notificationManager = null;
    public static NotificationCompat.Builder notificationBuilder = null;

    @RequiresApi(api = Build.VERSION_CODES.O)
    UploadNotification(Context context) {
        this.context = context;
        notificationBuilder = getUploadNotification(context);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(new NotificationChannel(
                UploadTask.NOTIFICATION_CHANNEL_ID,
                UploadTask.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        ));
    }

    public void updateProgress() {
        float totalProgressStore = ((float) AckDatabase.getInstance(context).pendingUploadDao().getCompletedUploadsCount()) / AckDatabase.getInstance(context).pendingUploadDao().getAll().size();
        notificationBuilder.setProgress(100, (int) (totalProgressStore * 100f), false);
        notificationManager.notify(UploadNotification.notificationId, notificationBuilder.build());
    }

    public static void configure(final String title, @IntegerRes final int icon, final String intentActivity) {
        notificationTitle = title;
        notificationIconRes = icon;
        notificationIntentActivity = intentActivity;
    }

    public static Notification createNotification(NotificationCompat.Builder notificationBuilder) {
        Notification notification = notificationBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        return  notification;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationCompat.Builder getUploadNotification(final Context context) {
        Class<?> mainActivityClass = null;
        try {
            mainActivityClass = Class.forName(notificationIntentActivity);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Intent notificationIntent = new Intent(context, mainActivityClass);
        int pendingIntentFlag;
        if (Build.VERSION.SDK_INT >= 23) {
            pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE;
        } else {
            pendingIntentFlag = 0;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, pendingIntentFlag);

        // TODO: click intent open app
        @SuppressLint("ResourceType") NotificationCompat.Builder uploadNotificationBuilder = new NotificationCompat.Builder(context, UploadTask.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setTicker(notificationTitle)
                .setSmallIcon(notificationIconRes)
                .setColor(Color.rgb(57, 100, 150))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, 0, false)
                .setChannelId(UploadTask.NOTIFICATION_CHANNEL_ID)
                .addAction(notificationIconRes, "Open", pendingIntent);

        return uploadNotificationBuilder;
    }
}
