package com.spoon.backgroundfileupload;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.util.Log;

import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork;
import com.sromku.simple.storage.SimpleStorage;
import com.sromku.simple.storage.Storage;
import com.sromku.simple.storage.helpers.OrderType;

import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.UploadServiceConfig;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.data.UploadNotificationStatusConfig;
import net.gotev.uploadservice.exceptions.UserCancelledUploadException;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.RequestObserver;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class FileTransferBackground extends CordovaPlugin {
    private CallbackContext uploadCallback;
    private boolean isNetworkAvailable = false;
    private Long lastProgressTimestamp = 0L;
    private boolean ready = false;
    private Disposable networkObservable;
    private RequestObserver globalObserver;
    private RequestObserverDelegate broadcastReceiver = new RequestObserverDelegate() {
        @Override
        public void onProgress(Context context, UploadInfo uploadInfo) {
            Long currentTimestamp = System.currentTimeMillis() / 1000;
            if (currentTimestamp - lastProgressTimestamp >= 1) {
                lastProgressTimestamp = currentTimestamp;
                sendCallback(new JSONObject(new HashMap() {{
                    put("id", uploadInfo.getUploadId());
                    put("progress", uploadInfo.getProgressPercent());
                    put("state", "UPLOADING");
                }}));
            }
        }

        @Override
        public void onError(final Context context, final UploadInfo uploadInfo, final Throwable exception) {
            String errorMsg = exception != null ? exception.getMessage() : "";
            logMessage("eventLabel='Uploader onError' uploadId='" + uploadInfo.getUploadId() + "' error='" + errorMsg + "'");
            deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "FAILED");
                put("error", "upload failed: " + errorMsg);
                put("errorCode", exception instanceof UserCancelledUploadException ? -999 : 0);
            }}));
        }

        @Override
        public void onSuccess(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
            logMessage("eventLabel='Uploader onSuccess' uploadId='" + uploadInfo.getUploadId() + "' response='" + serverResponse.getBodyString() + "'");
            deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "UPLOADED");
                put("serverResponse", serverResponse.getBodyString());
                put("statusCode", serverResponse.getCode());
            }}));
        }

        @Override
        public void onCompleted(Context context, UploadInfo uploadInfo) {
            logMessage("eventLabel='Uploader onCompleted' uploadId='" + uploadInfo.getUploadId() + "'");
        }

        @Override
        public void onCompletedWhileNotObserving() {
            logMessage("eventLabel='Uploader onCompletedWhileNotObserving'");
        }
    };

    public void createAndSendEvent(JSONObject obj) {
        UploadEvent event = UploadEvent.create(obj);
        sendCallback(event.dataRepresentation());
    }

    public void sendCallback(JSONObject obj) {
        /* we check the webview has been initialized */
        if (ready) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
            result.setKeepCallback(true);
            uploadCallback.sendPluginResult(result);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        FileTransferBackground self = this;
        if (action.equalsIgnoreCase("destroy")) {
            this.destroy();
            return true;
        }
        if (action.equalsIgnoreCase("initManager")) {
            self.initManager(args.get(0).toString(), callbackContext);
            return true;
        }
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    if (action.equalsIgnoreCase("removeUpload")) {
                        self.removeUpload(args.get(0).toString(), callbackContext);
                    } else if (action.equalsIgnoreCase("acknowledgeEvent")) {
                        self.acknowledgeEvent(args.getString(0), callbackContext);
                    } else if (action.equalsIgnoreCase("startUpload")) {
                        self.addUpload((JSONObject) args.get(0));
                    } else if (action.equalsIgnoreCase("destroy")) {
                        self.destroy();
                    }
                } catch (Exception exception) {
                    String message = "(" + exception.getClass().getSimpleName() + ") - " + exception.getMessage();
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, message);
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                    exception.printStackTrace();
                }
            }
        });
        return true;
    }

    private void initManager(String options, final CallbackContext callbackContext) throws IllegalStateException {
        if (this.ready) {
            throw new IllegalStateException("initManager was called twice");
        }
        this.uploadCallback = callbackContext;
        this.ready = true;
        this.globalObserver = new RequestObserver(this.cordova.getActivity().getApplicationContext(), broadcastReceiver);
        this.globalObserver.register();
        int parallelUploadsLimit = 1;
        try {
            JSONObject settings = new JSONObject(options);
            parallelUploadsLimit = settings.getInt("parallelUploadsLimit");
        } catch (JSONException error) {
            logMessage("eventLabel='Uploader could not read parallelUploadsLimit from config' error='" + error.getMessage() + "'");
        }
        ExecutorService threadPoolExecutor =
                new ThreadPoolExecutor(
                        parallelUploadsLimit,
                        parallelUploadsLimit,
                        5000,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<Runnable>()
                );
        UploadServiceConfig.setThreadPool((AbstractExecutorService) threadPoolExecutor);
        FileTransferBackground manager = this;
        //mark v1 uploads as failed
        migrateOldUploads();

        //broadcast all completed upload events
        for (UploadEvent event : UploadEvent.all()) {
            logMessage("Uploader send event missing on Start - " + event.getId());
            sendCallback(event.dataRepresentation());
        }

        networkObservable = ReactiveNetwork
                .observeNetworkConnectivity(cordova.getContext())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(connectivity -> {
                    logMessage("eventLabel='Uploader Network connectivity changed' connectivity_state='" + connectivity.state() + "'");
                    manager.isNetworkAvailable = connectivity.state() == NetworkInfo.State.CONNECTED;
                    if (manager.isNetworkAvailable) {
                        uploadPendingList();
                    }
                });
    }

    private void migrateOldUploads() {
        Storage storage = SimpleStorage.getInternalStorage(this.cordova.getActivity().getApplicationContext());
        String uploadDirectoryName = "FileTransferBackground";
        if (storage.isDirectoryExists(uploadDirectoryName)) {
            for (String uploadId : getOldUploadIds()) {
                UploadEvent event = UploadEvent.create(new JSONObject(new HashMap() {{
                    put("id", uploadId);
                    put("state", "FAILED");
                    put("errorCode", 0);
                    put("error", "upload failed");
                }}));
            }
            // remove all old uploads
            storage.deleteDirectory(uploadDirectoryName);
        }
    }

    private ArrayList<String> getOldUploadIds() {
        Storage storage = SimpleStorage.getInternalStorage(this.cordova.getActivity().getApplicationContext());
        String uploadDirectoryName = "FileTransferBackground";
        ArrayList<String> previousUploads = new ArrayList();
        List<File> files = storage.getFiles(uploadDirectoryName, OrderType.DATE);
        for (File file : files) {
            if (file.getName().endsWith(".json")) {
                String content = storage.readTextFile(uploadDirectoryName, file.getName());
                if (content != null) {
                    try {
                        previousUploads.add(new JSONObject(content).getString("id"));
                    } catch (JSONException exception) {
                        logMessage("eventLabel='Uploader could not read old uploads' error='" + exception.getMessage() + "'");
                    }
                }
            }
        }
        return previousUploads;
    }

    private void uploadPendingList() {
        List<PendingUpload> previousUploads = PendingUpload.all();
        for (PendingUpload upload : previousUploads) {
            JSONObject obj = null;
            try {
                obj = new JSONObject(upload.data);
            } catch (JSONException exception) {
                logMessage("eventLabel='Uploader could not parse pending upload' uploadId='" + upload.uploadId + "' error='" + exception.getMessage() + "'");
            }
            if (obj != null) {
                logMessage("eventLabel='Uploader upload pending list' uploadId='" + upload.uploadId + "'");
                this.startUpload(upload.dataHash());
            }
        }
    }

    private void addUpload(JSONObject jsonPayload) {
        HashMap payload = null;
        try {
            payload = FileTransferBackground.convertToHashMap(jsonPayload);
        } catch (JSONException error) {
            logMessage("eventLabel='Uploader could not read id from payload' error:'" + error.getMessage() + "'");
        }
        if (payload == null) return;
        String uploadId = payload.get("id").toString();

        if (PendingUpload.count(PendingUpload.class, "upload_id = ?", new String[]{uploadId}) > 0) {
            logMessage("eventLabel='Uploader an upload is already pending with this id' uploadId='" + uploadId + "'");
            return;
        }

        PendingUpload.create(jsonPayload);
        startUpload(payload);
    }

    private void startUpload(HashMap<String, Object> payload) {
        String uploadId = payload.get("id").toString();
        if (UploadService.getTaskList().contains(uploadId)) {
            logMessage("eventLabel='Uploader upload is already being uploaded. ignoring re-upload start' uploadId='" + uploadId + "'");
            return;
        }
        logMessage("eventLabel='Uploader starting upload' uploadId='" + uploadId + "'");
        if (!isNetworkAvailable) {
            logMessage("eventLabel='Uploader no network available, upload has been queued' uploadId='" + uploadId + "'");
            return;
        }
        MultipartUploadRequest request = null;
        try {
            request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), payload.get("serverUrl").toString())
                    .setUploadID(uploadId)
                    .setMethod("POST")
                    .addFileToUpload(payload.get("filePath").toString(), payload.get("fileKey").toString())
                    .setMaxRetries(0);
        } catch (IllegalArgumentException | FileNotFoundException error) {
            sendAddingUploadError(uploadId, error);
            return;
        }

        try {
            HashMap<String, Object> headers = convertToHashMap((JSONObject) payload.get("headers"));
            for (String key : headers.keySet()) {
                request.addHeader(key, headers.get(key).toString());
            }
        } catch (JSONException exception) {
            logMessage("eventLabel='could not parse request headers' uploadId='" + uploadId + "' error='" + exception.getMessage() + "'");
            sendAddingUploadError(uploadId, exception);
            return;
        }

        try {
            HashMap<String, Object> parameters = convertToHashMap((JSONObject) payload.get("parameters"));
            for (String key : parameters.keySet()) {
                request.addParameter(key, parameters.get(key).toString());
            }
        } catch (JSONException exception) {
            logMessage("eventLabel='could not parse request parameters' uploadId='" + uploadId + "' error='" + exception.getMessage() + "'");
            sendAddingUploadError(uploadId, exception);
            return;
        }

        String title = payload.get("notificationTitle").toString();
        request.setNotificationConfig((context, id) -> getNotificationConfiguration(title));
        request.startUpload();
    }

    private void sendAddingUploadError(String uploadId, Exception error) {
        deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
            put("id", uploadId);
            put("state", "FAILED");
            put("errorCode", 0);
            put("error", error.getMessage());
        }}));
    }

    public void deletePendingUploadAndSendEvent(JSONObject obj) {
        String id = null;
        try {
            id = obj.getString("id");
        } catch (JSONException error) {
            logMessage("eventLabel='Uploader could not delete pending upload' error='" + error.getMessage() + "'");
        }
        logMessage("eventLabel='Uploader delete pending upload' uploadId='" + id + "'");
        PendingUpload.remove(id);
        createAndSendEvent(obj);
    }

    private UploadNotificationStatusConfig buildNotificationStatusConfig(String title) {
        Activity mainActivity = cordova.getActivity();
        Resources activityRes = mainActivity.getResources();
        int iconId = activityRes.getIdentifier("ic_upload", "drawable", mainActivity.getPackageName());
        Intent intent = new Intent(cordova.getContext(), mainActivity.getClass());
        PendingIntent clickIntent = PendingIntent.getActivity(cordova.getContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new UploadNotificationStatusConfig(
                title != null ? title : "",
                "",
                iconId,
                Color.parseColor("#396496"),
                null,
                clickIntent,
                new ArrayList<>(0),
                true,
                true
        );
    }

    private UploadNotificationConfig getNotificationConfiguration(String title) {
        UploadNotificationConfig config = new UploadNotificationConfig(
                "com.spoon.backgroundfileupload.channel",
                false,
                buildNotificationStatusConfig(title),
                buildNotificationStatusConfig(null),
                buildNotificationStatusConfig(null),
                buildNotificationStatusConfig(null));
        return config;
    }

    public static HashMap<String, Object> convertToHashMap(JSONObject jsonObject) throws JSONException {
        HashMap<String, Object> hashMap = new HashMap<>();
        if (jsonObject != null) {
            Iterator<?> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                Object value = jsonObject.get(key);
                hashMap.put(key, value);
            }
        }
        return hashMap;
    }

    public static void logMessage(String message) {
        Log.d("CordovaBackgroundUpload", message);
    }

    private void removeUpload(String uploadId, CallbackContext context) {
        PendingUpload.remove(uploadId);
        UploadService.stopUpload(uploadId);
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
    }

    private void acknowledgeEvent(String eventId, CallbackContext context) {
        UploadEvent.destroy(Long.valueOf(eventId.replaceAll("\\D+", "")).longValue());
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
    }

    public void onDestroy() {
        logMessage("eventLabel='Uploader plugin onDestroy'");
        destroy();
    }

    public void destroy() {
        this.ready = false;
        if (this.networkObservable != null) { this.networkObservable.dispose(); }
        if (this.globalObserver != null) { this.globalObserver.unregister(); }
        this.networkObservable = null;
        this.globalObserver = null;
    }
}
