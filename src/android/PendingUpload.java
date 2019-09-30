package com.spoon.backgroundfileupload;

import com.orm.SugarRecord;
import com.orm.dsl.Unique;
import com.orm.query.Condition;
import com.orm.query.Select;

import org.json.JSONObject;

import java.util.List;

public class PendingUpload extends SugarRecord {
    @Unique
    String uploadId;
    String data;

    public PendingUpload() {

    }

    public PendingUpload(JSONObject payload) {
        try {
            uploadId = payload.getString("id");
            data = payload.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void create(JSONObject payload) {
        new PendingUpload(payload).save();
    }


    public static void remove(String uploadId) {
        List<PendingUpload> results = Select.from(PendingUpload.class)
                .where(Condition.prop("upload_id").eq(uploadId))
                .list();
        if (results.size() > 0)
            results.get(0).delete();
    }

    public static List<PendingUpload> all() {
        return PendingUpload.listAll(PendingUpload.class);
    }
}
