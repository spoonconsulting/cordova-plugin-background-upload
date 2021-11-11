package com.spoon.backgroundfileupload;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import com.sharinpix.SharinPix.R;

import java.util.concurrent.Executors;

public class WorkManagerInit extends ContentProvider {
    @Override
    public boolean onCreate() {
        Integer concurrentUploads = Integer.parseInt(String.valueOf(getContext().getApplicationContext().getResources().getText(R.string.concurrentUploads)));
        Configuration configuration = new Configuration.Builder()
                .setExecutor(Executors.newFixedThreadPool(concurrentUploads))
                .build();

        WorkManager.initialize(getContext().getApplicationContext(), configuration);
        return true;
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
