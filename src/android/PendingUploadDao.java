package com.spoon.backgroundfileupload;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingUploadDao {
    @Query("SELECT * FROM pending_upload")
    List<PendingUpload> getAll();

    @Query("SELECT COUNT(*) FROM pending_upload")
    List<PendingUpload> getAllCount();

    @Query("SELECT * FROM pending_upload WHERE id = :id")
    PendingUpload getById(final String id);

    @Query("SELECT * FROM pending_upload WHERE state = 'PENDING' LIMIT 1")
    PendingUpload getFirstPendingEntry();

    @Query("SELECT COUNT(*) FROM pending_upload WHERE state = 'PENDING'")
    int getPendingUploadsCount();

    @Query("SELECT COUNT(*) FROM pending_upload WHERE state = 'UPLOADED'")
    int getCompletedUploadsCount();

    @Query("UPDATE pending_upload SET state = 'UPLOADED' WHERE ID = :id")
    void markAsUploaded(final String id);

    default boolean exists(final String id) {
        return getById(id) != null;
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(final PendingUpload ack);

    @Delete
    void delete(final PendingUpload ack);

    default void delete(final String id) {
        PendingUpload ack = getById(id);
        if (ack != null) {
            delete(ack);
        }
    }
}
