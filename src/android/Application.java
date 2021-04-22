package com.spoon.backgroundfileupload;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import net.gotev.uploadservice.UploadServiceConfig;
import net.gotev.uploadservice.okhttp.OkHttpStack;

public class Application extends com.orm.SugarApp {
    private static final String channelId = "com.spoon.backgroundfileupload.channel";
    private static final String channelName = "upload channel";

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        UploadServiceConfig.initialize(this, channelId, false);
        UploadServiceConfig.setHttpStack(new OkHttpStack());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
    }
}
