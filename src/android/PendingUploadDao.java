package com.spoon.backgroundfileupload;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PendingUploadDao {
    @Query("SELECT * FROM pending_upload")
    List<PendingUpload> getAll();

    @Query("SELECT COUNT(*) FROM pending_upload")
    int getAllCount();

    @Query("SELECT * FROM pending_upload WHERE id = :id")
    PendingUpload getById(final String id);

    @Query("SELECT COUNT(id) FROM pending_upload WHERE id = :id")
    int getCountById(final String id);

    @Query("SELECT * FROM pending_upload WHERE state = 'PENDING' LIMIT 1")
    PendingUpload getFirstPendingEntry();

    @Query("SELECT COUNT(*) FROM pending_upload WHERE state = 'PENDING'")
    int getPendingUploadsCount();

    @Query("SELECT * FROM pending_upload WHERE state = 'UPLOADED'")
    List<PendingUpload> getCompletedUploads();

    @Query("SELECT COUNT(*) FROM pending_upload WHERE state = 'UPLOADED'")
    int getCompletedUploadsCount();

    @Update(onConflict = OnConflictStrategy.REPLACE)
    default void markAsPending(final String id) {
        setState(id, "PENDING");
    }

    @Update(onConflict = OnConflictStrategy.REPLACE)
    default void markAsUploaded(final String id) {
        setState(id, "UPLOADED");
    }

    @Query("UPDATE OR REPLACE pending_upload SET state = :state WHERE ID = :id")
    void setState(final String id, final String state);

    default boolean exists(final String id) {
        return getCountById(id) > 0;
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(final PendingUpload ack);

    @Delete
    void delete(final PendingUpload ack);

    default void delete(final String id) {
        PendingUpload pendingUpload = getById(id);
        if (pendingUpload != null) {
            delete(pendingUpload);
        }
    }
}
