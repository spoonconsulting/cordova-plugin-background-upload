package com.spoon.backgroundFileUpload;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

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
import java.util.List;


public class FileTransferBackground extends CordovaPlugin {

    private final String uploadDirectoryName = "FileTransferBackground";
    private Storage storage;
    private CallbackContext uploadCallback;
    private NetworkMonitor networkMonitor;
    private Long lastProgressTimestamp = 0L;
    private boolean hasBeenDestroyed = false;

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
            logMessage("upload did fail: " + exception);
            JSONObject errorObj = new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "FAILED");
                put("error", "upload failed: " + exception != null ? exception.getMessage() : "");
                put("errorCode", 0);
            }});
            sendCallback(errorObj);
        }

        @Override
        public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
            logMessage("server response : " + serverResponse.getBodyAsString() + " for " + uploadInfo.getUploadId());
            JSONObject jsonObj = new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "UPLOADED");
                put("serverResponse", serverResponse.getBodyAsString());
                put("statusCode", serverResponse.getHttpCode());
            }});
            sendCallback(jsonObj);
        }

        @Override
        public void onCancelled(Context context, UploadInfo uploadInfo) {
            logMessage("upload cancelled " + uploadInfo.getUploadId());
            PendingUpload.remove(uploadInfo.getUploadId());
            if (hasBeenDestroyed) {
                //most likely the upload service was killed by the system
                return;
            }
            JSONObject jsonObj = new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "FAILED");
                put("errorCode", -999);
                put("error", "upload cancelled");
            }});
            sendCallback(jsonObj);
        }
    };

    public void sendCallback(JSONObject obj) {
        try {
            if (uploadCallback != null && !hasBeenDestroyed) {
                obj.put("platform", "android");
                PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                result.setKeepCallback(true);
                uploadCallback.sendPluginResult(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equalsIgnoreCase("initManager")) {
            uploadCallback = callbackContext;
            this.initManager(args.get(0).toString());
        } else if (action.equalsIgnoreCase("removeUpload")) {
            this.removeUpload(args.get(0).toString());
        } else {
            upload((JSONObject) args.get(0));
        }
        return true;
    }

    private void upload(JSONObject jsonPayload) {
        try {
            final UploadPayload payload = new UploadPayload(jsonPayload.toString());
            logMessage("adding upload " + payload.id);
            if (NetworkMonitor.isConnected) {
                MultipartUploadRequest request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), payload.id, payload.serverUrl)
                        .addFileToUpload(payload.filePath, payload.fileKey)
                        .setMaxRetries(0);

                if (payload.showNotification) {
                    UploadNotificationConfig config = new UploadNotificationConfig();
                    config.getCompleted().autoClear = true;
                    config.getCancelled().autoClear = true;
                    config.getError().autoClear = true;
                    config.setClearOnActionForAllStatuses(true);
                    Intent intent = new Intent(cordova.getContext(), cordova.getActivity().getClass());
                    PendingIntent pendingIntent = PendingIntent.getActivity(cordova.getContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    config.setClickIntentForAllStatuses(pendingIntent);
                    if (payload.notificationTitle != null)
                        config.getProgress().title = payload.notificationTitle;
                    request.setNotificationConfig(config);
                }
                for (String key : payload.parameters.keySet()) {
                    request.addParameter(key, payload.parameters.get(key));
                }

                for (String key : payload.headers.keySet()) {
                    request.addHeader(key, payload.headers.get(key));
                }

                request.startUpload();
                PendingUpload.remove(payload.id);
            } else {
                logMessage("No network available, adding upload (" + payload.id + ") to queue");
                PendingUpload.create(jsonPayload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logMessage(String message) {
        Log.d("CordovaBackgroundUpload", message);
    }

    private void removeUpload(String fileId) {
        PendingUpload.remove(fileId);
        UploadService.stopUpload(fileId);
    }

    private ArrayList<JSONObject> getUploadHistory() throws JSONException {
        ArrayList<JSONObject> previousUploads = new ArrayList<JSONObject>();
        List<File> files = storage.getFiles(uploadDirectoryName, OrderType.DATE);
        for (File file : files) {
            if (file.getName().endsWith(".json")) {
                String content = storage.readTextFile(uploadDirectoryName, file.getName());
                if (content != null) {
                    previousUploads.add(new JSONObject(content));
                }
            }
        }
        return previousUploads;
    }

    private void initManager(String options) {
        try {

            int parallelUploadsLimit = 1;
            if (options != null) {
                JSONObject settings = new JSONObject(options);
                parallelUploadsLimit = settings.getInt("parallelUploadsLimit");
            }

            UploadService.HTTP_STACK = new OkHttpStack();
            UploadService.UPLOAD_POOL_SIZE = parallelUploadsLimit;
            UploadService.NAMESPACE = cordova.getContext().getPackageName();
            storage = SimpleStorage.getInternalStorage(this.cordova.getActivity().getApplicationContext());

            cordova.getActivity().getApplicationContext().registerReceiver(broadcastReceiver, new IntentFilter(UploadService.NAMESPACE + ".uploadservice.broadcast.status"));

            //mark v1 uploads as failed
            migrateOldUploads();

            networkMonitor = new NetworkMonitor(webView.getContext(), new ConnectionStatusListener() {
                @Override
                public void connectionDidChange(Boolean isConnected, String networkType) {
                    try {
                        if (isConnected) {
                            logMessage("Network (" + networkType + ") now available, restarting pending uploads");
                            uploadPendingList();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void migrateOldUploads() throws JSONException {
        ArrayList<JSONObject> previousUploads = getUploadHistory();
        for (JSONObject upload : previousUploads) {
            String uploadId = upload.getString("id");
            JSONObject jsonObj = new JSONObject(new HashMap() {{
                put("id", uploadId);
                put("state", "FAILED");
                put("errorCode", 500);
                put("error", "upload failed");
            }});
            sendCallback(jsonObj);
            storage.deleteFile(uploadDirectoryName, uploadId + ".json");
        }
    }

    private void uploadPendingList() throws JSONException {
        List<PendingUpload> previousUploads = PendingUpload.all();
        for (PendingUpload upload : previousUploads) {
            this.upload(new JSONObject(upload.data));
        }
    }

    public void onDestroy() {
        logMessage("plugin onDestroy, unsubscribing all callbacks");
        hasBeenDestroyed = true;
        if (networkMonitor != null)
            networkMonitor.stopMonitoring();
        //broadcastReceiver.unregister(cordova.getActivity().getApplicationContext());
    }
}