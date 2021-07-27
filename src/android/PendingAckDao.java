package com.spoon.backgroundfileupload;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingAckDao {
    @Query("SELECT * FROM pending_ack")
    List<PendingAck> getAll();

    @Query("SELECT * FROM pending_ack WHERE id = :id")
    PendingAck getById(final String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(final PendingAck ack);

    @Delete
    void delete(final PendingAck ack);

    default void delete(final String id) {
        PendingAck ack = getById(id);
        if (ack != null) {
            delete(ack);
        }
    }
}
