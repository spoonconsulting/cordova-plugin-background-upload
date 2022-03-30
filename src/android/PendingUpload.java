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
    private Data outputData;

    public PendingUpload(@NonNull final String id, @NonNull final Data outputData) {
        this.id = id;
        this.outputData = outputData;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Data getOutputData() {
        return outputData;
    }
}
