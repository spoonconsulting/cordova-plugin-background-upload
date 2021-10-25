package com.spoon.backgroundfileupload;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.work.Data;

@Database(entities = {UploadEvent.class}, version = 3)
@TypeConverters(value = {Data.class})
public abstract class AckDatabase extends RoomDatabase {
    private static AckDatabase instance;

    public static AckDatabase getInstance(final Context context) {
        if (instance == null) {
            instance = Room
                    .databaseBuilder(context, AckDatabase.class, "cordova-plugin-background-upload.db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }

    public abstract UploadEventDao uploadEventDao();
}
