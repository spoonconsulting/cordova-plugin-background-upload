package com.spoon.backgroundfileupload;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UploadEventDao {
    @Query("SELECT * FROM upload_event")
    List<UploadEvent> getAll();

    @Query("SELECT * FROM upload_event WHERE id = :id")
    UploadEvent getById(final String id);

    default boolean exists(final String id) {
        return getById(id) != null;
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(final UploadEvent ack);

    @Delete
    void delete(final UploadEvent ack);

    default void delete(final String id) {
        UploadEvent ack = getById(id);
        if (ack != null) {
            delete(ack);
        }
    }
}
