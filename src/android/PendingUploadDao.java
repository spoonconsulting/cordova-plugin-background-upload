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

    @Query("SELECT * FROM pending_upload WHERE id = :id")
    PendingUpload getById(final String id);

    @Query("SELECT * FROM pending_upload LIMIT 1")
    PendingUpload getFirstEntry();

    @Query("SELECT * FROM pending_upload WHERE state = 'PENDING' ORDER BY ID DESC LIMIT 1")
    PendingUpload getLastPendingUpload();

    @Query("SELECT COUNT(id) FROM pending_upload WHERE state = 'UPLOADING'")
    int getNumberOfUploadingUploads();

    @Query("UPDATE pending_upload SET state = :state WHERE ID = :id")
    void setState(final String id, final String state);

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
