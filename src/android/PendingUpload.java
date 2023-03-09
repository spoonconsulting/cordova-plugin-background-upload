package com.spoon.backgroundfileupload;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.work.Data;

@Entity(tableName = "pending_upload")
public class PendingUpload {
    @PrimaryKey
    @NonNull
    private String id;

    @ColumnInfo(name = "output_data")
    @NonNull
    private Data inputData;

    @ColumnInfo(name = "state")
    @NonNull
    private String state;

    public PendingUpload(@NonNull final String id, @NonNull final Data inputData) {
        this.id = id;
        this.inputData = inputData;
        this.state = "PENDING";
    }

    @NonNull
    public void setState(@NonNull final String state) {
        this.state = state;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Data getInputData() {
        return inputData;
    }

    @NonNull
    public String getState() {
        return state;
    }
}
