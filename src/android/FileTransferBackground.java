package com.spoon.backgroundfileupload;

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
import java.util.Iterator;
import java.util.List;


public class FileTransferBackground extends CordovaPlugin {

    private CallbackContext uploadCallback;
    private NetworkMonitor networkMonitor;
    private Long lastProgressTimestamp = 0L;
    private boolean hasBeenDestroyed = false;

    private UploadServiceBroadcastReceiver broadcastReceiver = new UploadServiceBroadcastReceiver() {
        @Override
        public void onProgress(Context context, UploadInfo uploadInfo) {
            logMessage("upload " + uploadInfo.getUploadId() + " progress: " + uploadInfo.getProgressPercent());
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
            JSONObject errorObj = new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "FAILED");
                put("error", "upload failed: " + exception != null ? exception.getMessage() : "");
                put("errorCode", 0);
            }});
            createAndSendEvent(errorObj);
        }

        @Override
        public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
            logMessage("upload " + uploadInfo.getUploadId() + " completed with response : " + serverResponse.getBodyAsString() + " for " + uploadInfo.getUploadId());
            JSONObject jsonObj = new JSONObject(new HashMap() {{
                put("id", uploadInfo.getUploadId());
                put("state", "UPLOADED");
                put("serverResponse", serverResponse.getBodyAsString());
                put("statusCode", serverResponse.getHttpCode());
            }});
            createAndSendEvent(jsonObj);
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
            createAndSendEvent(jsonObj);
        }
    };

    public void createAndSendEvent(JSONObject obj) {
        try {
            PendingUpload.remove(obj.getString("id"));
            UploadEvent event = UploadEvent.create(obj);
            obj.put("eventId", event.getId());
            sendCallback(obj);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
        } else if (action.equalsIgnoreCase("acknowledgeEvent")) {
            this.acknowledgeEvent(args.getLong(0));
        } else if (action.equalsIgnoreCase("startUpload")) {
            upload((JSONObject) args.get(0));
        }
        return true;
    }

    private void upload(JSONObject jsonPayload) {
        String id = null;
        try {
            id = jsonPayload.getString("id");
            if (UploadService.getTaskList().contains(id)) {
                logMessage("upload " + id + " is already being uploaded. ignoring re-upload request");
                return;
            }
            logMessage("adding upload " + id);
            PendingUpload.create(jsonPayload);
            if (NetworkMonitor.isConnected) {
                MultipartUploadRequest request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), id,
                        jsonPayload.getString("serverUrl"))
                        .addFileToUpload(jsonPayload.getString("filePath"), jsonPayload.getString("fileKey"))
                        .setMaxRetries(0);

                if (jsonPayload.getBoolean("showNotification")) {
                    UploadNotificationConfig config = new UploadNotificationConfig();
                    config.getCompleted().autoClear = true;
                    config.getCancelled().autoClear = true;
                    config.getError().autoClear = true;
                    config.setClearOnActionForAllStatuses(true);
                    Intent intent = new Intent(cordova.getContext(), cordova.getActivity().getClass());
                    PendingIntent pendingIntent = PendingIntent.getActivity(cordova.getContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    config.setClickIntentForAllStatuses(pendingIntent);
                    String notificationTitle = null;
                    if (jsonPayload.has("notificationTitle"))
                        notificationTitle = jsonPayload.getString("notificationTitle");
                    if (notificationTitle != null)
                        config.getProgress().title = notificationTitle;
                    request.setNotificationConfig(config);
                }

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
                logMessage("No network available, queueing upload (" + id + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
            String uploadId = id;
            JSONObject jsonObj = new JSONObject(new HashMap() {{
                put("id", uploadId);
                put("state", "FAILED");
                put("errorCode", 500);
                put("error", e.getLocalizedMessage());
            }});
            sendCallback(jsonObj);
            PendingUpload.remove(uploadId);
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

    private void removeUpload(String fileId) {
        PendingUpload.remove(fileId);
        UploadService.stopUpload(fileId);
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
            cordova.getActivity().getApplicationContext().registerReceiver(broadcastReceiver, new IntentFilter(UploadService.NAMESPACE + ".uploadservice.broadcast.status"));

            //mark v1 uploads as failed
            migrateOldUploads();

            //broadcast all completed upload events
            for (UploadEvent event : UploadEvent.all()) {
                sendCallback(event.dataRepresentation());
            }

            //re-launch any pending uploads
            uploadPendingList();

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
        ArrayList<JSONObject> previousUploads = getOldUploads();
        for (JSONObject upload : previousUploads) {
            String uploadId = upload.getString("id");
            JSONObject jsonObj = new JSONObject(new HashMap() {{
                put("id", uploadId);
                put("state", "FAILED");
                put("errorCode", 500);
                put("error", "upload failed");
            }});
            sendCallback(jsonObj);
        }
    }

    private ArrayList<JSONObject> getOldUploads() throws JSONException {
        Storage storage = SimpleStorage.getInternalStorage(this.cordova.getActivity().getApplicationContext());
        String uploadDirectoryName = "FileTransferBackground";
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
        //remove all uploads
        storage.deleteDirectory(uploadDirectoryName);
        return previousUploads;
    }

    private void uploadPendingList() throws JSONException {
        List<PendingUpload> previousUploads = PendingUpload.all();
        for (PendingUpload upload : previousUploads) {
            this.upload(new JSONObject(upload.data));
        }
    }

    private void acknowledgeEvent(Long eventId) {
        UploadEvent.destroy(eventId);
    }

    public void onDestroy() {
        logMessage("plugin onDestroy, stopping network monitor");
        hasBeenDestroyed = true;
        if (networkMonitor != null)
            networkMonitor.stopMonitoring();
//        broadcastReceiver.unregister(cordova.getActivity().getApplicationContext());
    }
}