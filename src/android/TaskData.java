package com.spoon.backgroundfileupload;

import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.data.UploadNotificationConfig;

/**
 * Created by Nick Adna on 3/28/2020.
 */

public class TaskData {

    private AbstractSingleNotificationHandler.TaskStatus status;
    private UploadInfo info;
    private UploadNotificationConfig config;

    public TaskData(AbstractSingleNotificationHandler.TaskStatus status, UploadInfo info, UploadNotificationConfig config) {
        this.status = status;
        this.info = info;
        this.config = config;
    }

    public AbstractSingleNotificationHandler.TaskStatus getStatus() {
        return status;
    }

    public UploadInfo getInfo() {
        return info;
    }
}