package com.spoon.backgroundfileupload;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import java.util.concurrent.Executors;

public class WorkManagerInit extends ContentProvider {

    private static final int defaultConcurrentUploads = 2;

    @Override
    public boolean onCreate() {
        int concurrentUploadsIdentifier = getContext().getApplicationContext().getResources().getIdentifier("concurrentUploads", "string", getContext().getApplicationContext().getPackageName());
        int concurrentUploads;
        if (concurrentUploadsIdentifier != 0) {
            concurrentUploads = Integer.parseInt(getContext().getApplicationContext().getResources().getString(concurrentUploadsIdentifier));
        } else {
            concurrentUploads = defaultConcurrentUploads;
        }

        Configuration configuration = new Configuration.Builder()
                .setExecutor(Executors.newFixedThreadPool(concurrentUploads))
                .build();

        WorkManager.initialize(getContext().getApplicationContext(), configuration);
        return true;
    }

    public static boolean isResource(Context context, int resId){
        if (context != null){
            try {
                return context.getResources().getResourceName(resId) != null;
            } catch (Resources.NotFoundException ignore) {
            }
        }
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] strings, @Nullable String s, @Nullable String[] strings1, @Nullable String s1) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }
}
