package com.spoon.backgroundfileupload;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.util.Log;

import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork;
import com.sromku.simple.storage.SimpleStorage;
import com.sromku.simple.storage.Storage;
import com.sromku.simple.storage.helpers.OrderType;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.UploadServiceBroadcastReceiver;
import net.gotev.uploadservice.okhttp.OkHttpStack;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class FileTransferBackground extends CordovaPlugin {

    private CallbackContext uploadCallback;
    private boolean isNetworkAvailable = false;
    private Long lastProgressTimestamp = 0L;
    private boolean ready = false;
    private Disposable networkObservable;

    private UploadServiceBroadcastReceiver broadcastReceiver = new UploadServiceBroadcastReceiver() {
        @Override
        public void onProgress(Context context, UploadInfo uploadInfo) {
            Long currentTimestamp = System.currentTimeMillis() / 1000;
            if (currentTimestamp - lastProgressTimestamp >= 1) {
                lastProgressTimestamp = currentTimestamp;
                JSONObject objResult = new JSONObject(new HashMap() {{
                    put("id", uploadInfo.getUploadId());
                    put("progress", uploadInfo.getProgressPercent());
                    put("state", "UPLOADING");
                }});
                sendCallback(objResult);
            }
        }

        @Override
        public void onError(final Context context, final UploadInfo uploadInfo, final ServerResponse serverResponse, final Exception exception) {
            logMessage("upload " + uploadInfo.getUploadId() + " failed: " + exception);
            deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "FAILED");
                put("error", "upload failed: " + exception != null ? exception.getMessage() : "");
                put("errorCode", serverResponse != null ? serverResponse.getHttpCode() : 0);
            }}));
        }

        @Override
        public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
            logMessage("upload " + uploadInfo.getUploadId() + " completed with response : " + serverResponse.getBodyAsString() + " for " + uploadInfo.getUploadId());
            deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "UPLOADED");
                put("serverResponse", serverResponse.getBodyAsString());
                put("statusCode", serverResponse.getHttpCode());
            }}));
        }

        @Override
        public void onCancelled(Context context, UploadInfo uploadInfo) {
            logMessage("upload cancelled " + uploadInfo.getUploadId());
            deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "FAILED");
                put("errorCode", -999);
                put("error", "upload cancelled");
            }}));
        }
    };

    public void deletePendingUploadAndSendEvent(JSONObject obj) {
        PendingUpload.remove(uploadInfo.getUploadId());
        createAndSendEvent(obj);
    }
    
    public void createAndSendEvent(JSONObject obj) {
        UploadEvent event = UploadEvent.create(obj);
        sendCallback(event.dataRepresentation());
    }

    public void sendCallback(JSONObject obj) {
        if (!hasBeenDestroyed) { // we check the webview has not been destroyed
            PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
            result.setKeepCallback(true);
            uploadCallback.sendPluginResult(result);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equalsIgnoreCase("initManager")) {
            uploadCallback = callbackContext;
            this.initManager(args.get(0).toString());
        } else if (action.equalsIgnoreCase("removeUpload")) {
            this.removeUpload(args.get(0).toString(), callbackContext);
        } else if (action.equalsIgnoreCase("acknowledgeEvent")) {
            this.acknowledgeEvent(args.getString(0), callbackContext);
        } else if (action.equalsIgnoreCase("startUpload")) {
            upload((JSONObject) args.get(0));
        }
        return true;
    }

    private void upload(JSONObject jsonPayload) {
        String id = null;
        id = jsonPayload.getString("id");
        if (UploadService.getTaskList().contains(id)) {
            logMessage("upload " + id + " is already being uploaded. ignoring re-upload request");
            return;
        }
        logMessage("adding upload " + id);
        PendingUpload.create(jsonPayload);
        if (isNetworkAvailable) {
            try {
                MultipartUploadRequest request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), id,
                    jsonPayload.getString("serverUrl"))
                    .addFileToUpload(jsonPayload.getString("filePath"), jsonPayload.getString("fileKey"))
                    .setMaxRetries(0);
            } catch {
                e.printStackTrace();
                deletePendingUploadAndSendEvent(new JSONObject(new HashMap() {{
                    put("id", id);
                    put("state", "FAILED");
                    put("errorCode", 0);
                    put("error", e.getLocalizedMessage());
                }}));
            }

            UploadNotificationConfig config = new UploadNotificationConfig();
            config.getCompleted().autoClear = true;
            config.getCancelled().autoClear = true;
            config.getError().autoClear = true;
            config.setClearOnActionForAllStatuses(true);
            Intent intent = new Intent(cordova.getContext(), cordova.getActivity().getClass());
            PendingIntent pendingIntent = PendingIntent.getActivity(cordova.getContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            config.setClickIntentForAllStatuses(pendingIntent);
            config.getProgress().title = jsonPayload.getString("notificationTitle"); // A mettre en JS :-)
            request.setNotificationConfig(config);

            HashMap<String, String> headers = convertToHashMap(jsonPayload.getJSONObject("headers"));
            for (String key : headers.keySet()) {
                request.addHeader(key, headers.get(key));
            }

            HashMap<String, String> parameters = convertToHashMap(jsonPayload.getJSONObject("parameters"));
            for (String key : parameters.keySet()) {
                request.addParameter(key, parameters.get(key));
            }

            request.startUpload();
        } else {
            logMessage("No network available, upload (" + id + ") has been queued");
        }
    }


    private HashMap<String, String> convertToHashMap(JSONObject jsonObject) throws JSONException {
        HashMap<String, String> hashMap = new HashMap<>();
        if (jsonObject != null) {
            Iterator<?> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                String value = jsonObject.getString(key);
                hashMap.put(key, value);
            }
        }
        return hashMap;
    }

    private void logMessage(String message) {
        Log.d("CordovaBackgroundUpload", message);
    }

    private void removeUpload(String fileId, CallbackContext context) {
        PendingUpload.remove(fileId);
        UploadService.stopUpload(fileId);
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
    }

    private void initManager(String options) {
        if(this.ready == true) { throw 'Already Active !'; }
        this.ready = true
        JSONObject settings = new JSONObject(options);
        int parallelUploadsLimit = settings.getInt("parallelUploadsLimit");

        UploadService.HTTP_STACK = new OkHttpStack();
        UploadService.UPLOAD_POOL_SIZE = parallelUploadsLimit;
        UploadService.NAMESPACE = cordova.getContext().getPackageName();
        cordova.getActivity().getApplicationContext().registerReceiver(broadcastReceiver, new IntentFilter(UploadService.NAMESPACE + ".uploadservice.broadcast.status"));

        networkObservable = ReactiveNetwork
                .observeNetworkConnectivity(cordova.getContext())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(connectivity -> {
                    this.isNetworkAvailable = connectivity.state() == NetworkInfo.State.CONNECTED;
                    if (this.isNetworkAvailable) {
                        logMessage("Network now available, restarting pending uploads");
                        uploadPendingList();
                    }
                });

        //mark v1 uploads as failed
        migrateOldUploads();

        //broadcast all completed upload events
        for (UploadEvent event : UploadEvent.all()) {
            sendCallback(event.dataRepresentation());
        }
    }

    private void migrateOldUploads() {
        for (String uploadId : OldUpload.getIds()) {
            createAndSendEvent(new JSONObject(new HashMap() {{
                put("id", uploadId);
                put("state", "FAILED");
                put("errorCode", 500);
                put("error", "upload failed");
            }}));
        }
    }

    private ArrayList<JSONObject> getOldUploadIds() {
        Storage storage = SimpleStorage.getInternalStorage(this.cordova.getActivity().getApplicationContext());
        String uploadDirectoryName = "FileTransferBackground";
        ArrayList<String> previousUploads = new ArrayList<String>();
        List<File> files = storage.getFiles(uploadDirectoryName, OrderType.DATE);
        for (File file : files) {
            if (file.getName().endsWith(".json")) {
                String content = storage.readTextFile(uploadDirectoryName, file.getName());
                if (content != null) {
                    try {
                      previousUploads.add(new JSONObject(content).getString("id"));
                    } catch {
                        log(content and co...)
                    }
                }
            }
        }
        // remove all uploads
        storage.deleteDirectory(uploadDirectoryName);
        return previousUploads;
    }

    private void uploadPendingList() {
        List<PendingUpload> previousUploads = PendingUpload.all();
        for (PendingUpload upload : previousUploads) {
            try { 
                obj = new JSONObject(upload.data)
            } catch  {
                log(upload.data)
            }
            if (obj != null) {
                this.upload(obj); 
            }
        }
    }

    private void acknowledgeEvent(String eventId, CallbackContext context) {
        String onlyNumbers = eventId.replaceAll("\\D+","");
        UploadEvent.destroy(Long.valueOf(onlyNumbers).longValue());
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
    }

    public void onDestroy() {
        logMessage("plugin onDestroy");
        ready = false;
        if (networkObservable != null)
            networkObservable.dispose();
//        broadcastReceiver.unregister(cordova.getActivity().getApplicationContext());
    }
}
