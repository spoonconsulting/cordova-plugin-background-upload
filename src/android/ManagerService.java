package com.spoon.backgroundfileupload;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.GlobalRequestObserver;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class ManagerService extends Service {

    private GlobalRequestObserver requestObserver;

    private RequestObserverDelegate broadcastReceiver = new RequestObserverDelegate() {
        @Override
        public void onProgress(Context context, UploadInfo uploadInfo) {

        }

        @Override
        public void onError(final Context context, final UploadInfo uploadInfo, final Throwable exception) {

        }

        @Override
        public void onSuccess(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
            deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "UPLOADED");
                put("serverResponse", serverResponse.getBodyString());
                put("statusCode", serverResponse.getCode());
            }}));
        }

        @Override
        public void onCompleted(Context context, UploadInfo uploadInfo) {
        }

        @Override
        public void onCompletedWhileNotObserving() {
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.requestObserver = new GlobalRequestObserver(this.getApplication(), broadcastReceiver);
        this.requestObserver.register();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void deletePendingUploadAndSendEvent(JSONObject obj) {
        String id = null;
        try {
            id = obj.getString("id");
        } catch (JSONException error) {
            error.getLocalizedMessage();
        }

        PendingUpload.remove(id);
    }
}
