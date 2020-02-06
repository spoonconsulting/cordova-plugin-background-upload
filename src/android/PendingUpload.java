package com.spoon.backgroundfileupload;

import android.util.Log;

import com.orm.SugarRecord;
import com.orm.dsl.Unique;
import com.orm.query.Condition;
import com.orm.query.Select;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

public class PendingUpload extends SugarRecord {
    String uploadId;
    String data;

    public PendingUpload() {}

    public PendingUpload(JSONObject payload) {
        try {
            uploadId = payload.getString("id");
            data = payload.toString();
        } catch (JSONException e) {
            FileTransferBackground.logMessage("eventLabel='Uploader error reading id during PendingUpload creation'");
        }
    }

    public HashMap<String, Object>dataHash() {
        try {
            return FileTransferBackground.convertToHashMap(new JSONObject(this.data));
        } catch (JSONException exception) {
            FileTransferBackground.logMessage("eventLabel='Uploader could not parse pending upload' uploadId='" + this.uploadId + "' error='" + exception.getMessage() + "'");
            return null;
        }
    }

    public static PendingUpload create(JSONObject payload) {
        PendingUpload pendingUpload = new PendingUpload(payload);
        pendingUpload.save();
        return pendingUpload;
    }

    public static void remove(String uploadId) {
        int deletedCount = PendingUpload.deleteAll(PendingUpload.class, "upload_id = ?", uploadId);
        FileTransferBackground.logMessage("eventLabel='Uploader delete pending upload' deleted_count="+deletedCount);
    }

    public static List<PendingUpload> all() {
        return PendingUpload.listAll(PendingUpload.class);
    }
}
