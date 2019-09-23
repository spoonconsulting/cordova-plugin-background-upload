package com.spoon.backgroundFileUpload;

import com.orm.SugarRecord;
import com.orm.dsl.Unique;
import com.orm.query.Condition;
import com.orm.query.Select;

import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

public class UploadEvent extends SugarRecord {
    @Unique
    String eventId;
    String data;

    public UploadEvent() {

    }

    public UploadEvent(JSONObject payload) {
        eventId = UUID.randomUUID().toString();
        data = payload.toString();
    }

    public static void create(JSONObject payload) {
        new UploadEvent(payload).save();
    }

    public static void destroy(String eventId) {
        List<UploadEvent> results = Select.from(UploadEvent.class)
                .where(Condition.prop("eventId").eq(eventId))
                .list();
        if (results.size() > 0)
            results.get(0).delete();
    }
}
