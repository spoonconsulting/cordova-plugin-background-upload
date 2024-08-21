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
    private long uploadDuration;
    private long uploadStartTime;
    private long uploadEndTime;


    @ColumnInfo(name = "output_data")
    @NonNull
    private Data outputData;

    public UploadEvent(@NonNull final String id, @NonNull final Data outputData) {
        this.id = id;
        this.outputData = outputData;
        this.uploadDuration = outputData.getLong(UploadTask.KEY_OUTPUT_UPLOAD_DURATION, 0);
        this.uploadStartTime = outputData.getLong(UploadTask.KEY_OUTPUT_UPLOAD_START_TIME, 0);
        this.uploadEndTime = outputData.getLong(UploadTask.KEY_OUTPUT_UPLOAD_END_TIME, 0);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Data getOutputData() {
        return outputData;
    }

    public long getUploadDuration() {
        return uploadDuration;
    }

    public void setUploadDuration(long uploadDuration) {
        this.uploadDuration = uploadDuration;
    }

    public long getUploadStartTime() {
        return uploadStartTime;
    }

    public void setUploadStartTime(long uploadStartTime) {
        this.uploadStartTime = uploadStartTime;
    }

    public long getUploadEndTime() {
        return uploadEndTime;
    }

    public void setUploadEndTime(long uploadEndTime) {
        this.uploadEndTime = uploadEndTime;
    }
}
