package com.spoon.backgroundfileupload;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.work.Data;

@Entity(tableName = "upload_event")
public class UploadEvent {
    @PrimaryKey
    @NonNull
    private String id;

    @ColumnInfo(name = "output_data")
    @NonNull
    private Data outputData;

    @ColumnInfo(name = "start_upload_time")
    @NonNull
    private long startUploadTime;

    @ColumnInfo(name = "finish_upload_time")
    @NonNull
    private long finishUploadTime;

    public UploadEvent(@NonNull final String id, @NonNull final Data outputData, @NonNull final long startUploadTime, @NonNull final long finishUploadTime) {
        this.id = id;
        this.outputData = outputData;
        this.startUploadTime = startUploadTime;
        this.finishUploadTime = finishUploadTime;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Data getOutputData() {
        return outputData;
    }

    public long getStartUploadTime() {
        return startUploadTime;
    }

    public long getFinishUploadTime() {
        return finishUploadTime;
    }

    public long calculateUploadDuration() {
        return finishUploadTime - startUploadTime;
    }
}
