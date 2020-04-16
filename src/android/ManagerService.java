package com.spoon.backgroundfileupload;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.exceptions.UserCancelledUploadException;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.GlobalRequestObserver;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;

import org.json.JSONObject;

import java.util.HashMap;

public class ManagerService extends Service {

    private GlobalRequestObserver requestObserver;
    private Long lastProgressTimestamp = 0L;
    private final IBinder mBinder = new LocalBinder();
    private Callbacks cb;

    private RequestObserverDelegate broadcastReceiver = new RequestObserverDelegate() {
        @Override
        public void onProgress(Context context, UploadInfo uploadInfo) {
            Long currentTimestamp = System.currentTimeMillis() / 1000;
            if (currentTimestamp - lastProgressTimestamp >= 1) {
                lastProgressTimestamp = currentTimestamp;

                JSONObject data = new JSONObject(new HashMap() {{
                    put("id", uploadInfo.getUploadId());
                    put("progress", uploadInfo.getProgressPercent());
                    put("state", "UPLOADING");
                }});

                cb.progressCallback(data);
            }
        }

        @Override
        public void onError(final Context context, final UploadInfo uploadInfo, final Throwable exception) {
            String errorMsg = exception != null ? exception.getMessage() : "";

            JSONObject data = new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "FAILED");
                put("error", "upload failed: " + errorMsg);
                put("errorCode", exception instanceof UserCancelledUploadException ? -999 : 0);
            }});

            cb.errorCallback(data);
        }

        @Override
        public void onSuccess(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
            JSONObject data = new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "UPLOADED");
                put("serverResponse", serverResponse.getBodyString());
                put("statusCode", serverResponse.getCode());
            }});

            cb.successCallback(data);
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
        return mBinder;
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

    public class LocalBinder extends Binder {
        public ManagerService getServiceInstance() {
            return ManagerService.this;
        }
    }

    public void registerClient(Callbacks callback) {
        this.cb = callback;
    }

    public interface Callbacks {
        void progressCallback(JSONObject data);

        void errorCallback(JSONObject data);

        void successCallback(JSONObject data);
    }
}
