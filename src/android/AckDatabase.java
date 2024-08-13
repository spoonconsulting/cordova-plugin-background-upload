package com.spoon.backgroundfileupload;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.work.Data;

@Database(entities = {UploadEvent.class}, version = 6)
@TypeConverters(value = {Data.class})
public abstract class AckDatabase extends RoomDatabase {
    private static AckDatabase instance;

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add the new column to the existing table
            database.execSQL("ALTER TABLE upload_events ADD COLUMN uploadDuration INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static synchronized AckDatabase getInstance(final Context context) {
        if (instance == null) {
            instance = Room
                    .databaseBuilder(context, AckDatabase.class, "cordova-plugin-background-upload.db")
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_5_6)
                    .build();
        }
        return instance;
    }

    public static void closeInstance() {
        // not called for now
        instance.close();
        instance = null;
    }

    public abstract UploadEventDao uploadEventDao();
}
