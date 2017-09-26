package com.spoon.backgroundFileUpload;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.content.Context;
import android.util.Log;


import com.sromku.simple.storage.SimpleStorage;
import com.sromku.simple.storage.Storage;
import com.sromku.simple.storage.helpers.OrderType;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.UploadStatusDelegate;
import net.gotev.uploadservice.okhttp.OkHttpStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class FileTransferBackground extends CordovaPlugin {

    private final String uploadDirectoryName = "FileTransferBackground";
    private Storage storage;

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        try {

            if (action.equalsIgnoreCase("initManager")) {
                this.initManager(args.length() > 0 ? args.get(0).toString() : null, callbackContext);
            }
            else if (action.equalsIgnoreCase("removeUpload")) {
                this.removeUpload(args.length() > 0 ? args.get(0).toString() : null, callbackContext);
            } else {

                final FileTransferSettings payload = new FileTransferSettings((args.get(0)).toString());

                MultipartUploadRequest request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), payload.serverUrl)
                        .addFileToUpload(payload.filePath, "file")
                        .setDelegate(new UploadStatusDelegate() {
                            @Override
                            public void onProgress(Context context, UploadInfo uploadInfo) {
                                LogMessage("id:" + payload.id + " progress: " + uploadInfo.getProgressPercent());

                                try {
                                    JSONObject objResult = new JSONObject();
                                    objResult.put("id", payload.id);
                                    objResult.put("progress", uploadInfo.getProgressPercent());
                                    objResult.put("state", "UPLOADING");
                                    PluginResult progressUpdate = new PluginResult(PluginResult.Status.OK, objResult);
                                    progressUpdate.setKeepCallback(true);
                                    callbackContext.sendPluginResult(progressUpdate);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onError(Context context, UploadInfo uploadInfo, Exception exception) {

                                LogMessage("App onError: " + exception);

                                try {
                                    updateStateForUpload(payload.id, UploadState.FAILED, null);

                                    JSONObject errorObj = new JSONObject();
                                    errorObj.put("id", payload.id);
                                    errorObj.put("error", "upload failed");
                                    errorObj.put("state", "FAILED");
                                    PluginResult errorResult = new PluginResult(PluginResult.Status.ERROR, errorObj);
                                    errorResult.setKeepCallback(true);
                                    callbackContext.sendPluginResult(errorResult);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {

                                try {
                                    LogMessage("server response : " + serverResponse.getBodyAsString());
                                    updateStateForUpload(payload.id, UploadState.UPLOADED, serverResponse.getBodyAsString());

                                    JSONObject objResult = new JSONObject();
                                    objResult.put("id", payload.id);
                                    objResult.put("completed", true);
                                    objResult.put("serverResponse", serverResponse.getBodyAsString());
                                    objResult.put("state", "UPLOADED");
                                    PluginResult completedUpdate = new PluginResult(PluginResult.Status.OK, objResult);
                                    completedUpdate.setKeepCallback(true);
                                    callbackContext.sendPluginResult(completedUpdate);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onCancelled(Context context, UploadInfo uploadInfo) {
                                LogMessage("App cancel");
                            }
                        });

                for (String key : payload.parameters.keySet()) {
                    request.addParameter(key, payload.parameters.get(key));
                }

                for (String key : payload.headers.keySet()) {
                    request.addHeader(key, payload.headers.get(key));
                }

                request.startUpload();
                this.createUploadInfoFile(payload.id);
            }


        } catch (Exception ex) {
            try {
                JSONObject errorObj = new JSONObject();
                errorObj.put("error", ex.getMessage());
                PluginResult errorResult = new PluginResult(PluginResult.Status.ERROR, errorObj);
                errorResult.setKeepCallback(true);
                callbackContext.sendPluginResult(errorResult);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private void LogMessage(String message) {

        Log.d("FileTransferBG", message);
    }

      private void removeUpload(String fileId, CallbackContext callbackContext) {
        try {
            if (fileId == null)
                return;
            UploadService.stopUpload(fileId);
            removeUploadInfoFile(fileId);
            PluginResult res = new PluginResult(PluginResult.Status.OK);
            res.setKeepCallback(true);
            callbackContext.sendPluginResult(res);
        } catch (Exception e) {
            e.printStackTrace();
            PluginResult errorResult = new PluginResult(PluginResult.Status.ERROR, e.toString());
            errorResult.setKeepCallback(true);
            callbackContext.sendPluginResult(errorResult);
        }
    }

    private void createUploadInfoFile(String fileId) {
        try {
            JSONObject upload = new JSONObject();
            upload.put("id", fileId);
            upload.put("createdDate", System.currentTimeMillis() / 1000);
            upload.put("state", UploadState.STARTED);

            storage.createFile(uploadDirectoryName, fileId + ".json", upload.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void updateStateForUpload(String fileId, String state, String serverResponse) {
        try {
            String content = storage.readTextFile(uploadDirectoryName, fileId + ".json");
            if (content != null) {
                JSONObject uploadJson = new JSONObject(content);
                uploadJson.put("state", state);
                if (state == UploadState.UPLOADED) {
                    uploadJson.put("serverResponse", serverResponse != null ? serverResponse : "");
                }
                //delete old file
                removeUploadInfoFile(fileId);
                //write updated file
                storage.createFile(uploadDirectoryName, fileId + ".json", uploadJson.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void removeUploadInfoFile(String fileId) {
        storage.deleteFile(uploadDirectoryName, fileId + ".json");
    }

    private ArrayList<JSONObject> getUploadHistory() {
        ArrayList<JSONObject> previousUploads = new ArrayList<JSONObject>();
        try {

            List<File> files = storage.getFiles(uploadDirectoryName, OrderType.DATE);
            for (File file : files) {
                if (file.getName().endsWith(".json")) {
                    String content = storage.readTextFile(uploadDirectoryName, file.getName());
                    if (content != null) {
                        JSONObject uploadJson = new JSONObject(content);
                        previousUploads.add(uploadJson);
                    }

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return previousUploads;
    }

    private void initManager(String options, final CallbackContext callbackContext) {
        try {

            UploadService.HTTP_STACK = new OkHttpStack();
            UploadService.UPLOAD_POOL_SIZE =1;

            storage = SimpleStorage.getInternalStorage(this.cordova.getActivity().getApplicationContext());
            storage.createDirectory(uploadDirectoryName);

            LogMessage("created working directory ");

            if (options != null) {
                //initialised global configuration parameters here
                //JSONObject settings = new JSONObject(options);
            }

            ArrayList<JSONObject> previousUploads = getUploadHistory();
            for (JSONObject upload: previousUploads){
                String state=upload.getString("state");
                String id=upload.getString("id");

                if (state.equalsIgnoreCase(UploadState.UPLOADED)){

                    JSONObject objResult = new JSONObject();
                    objResult.put("id", id);
                    objResult.put("completed", true);
                    objResult.put("serverResponse", upload.getString("serverResponse"));
                    PluginResult progressUpdate = new PluginResult(PluginResult.Status.OK, objResult);
                    progressUpdate.setKeepCallback(true);
                    callbackContext.sendPluginResult(progressUpdate);

                }else if (state.equalsIgnoreCase(UploadState.FAILED) || state.equalsIgnoreCase(UploadState.STARTED)){
                    //if the state is STARTED, it means app was killed before the upload was completed
                    JSONObject errorObj = new JSONObject();
                    errorObj.put("id", id);
                    errorObj.put("error", "upload failed");
                    PluginResult errorResult = new PluginResult(PluginResult.Status.ERROR, errorObj);
                    errorResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(errorResult);
                }
                //delete upload info on disk
                removeUploadInfoFile(id);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}

class UploadState {
    public static final String UPLOADED = "UPLOADED";
    public static final String FAILED = "FAILED";
    public static final String STARTED = "STARTED";
}

class FileTransferSettings {

    String filePath = "";
    String serverUrl = "";
    String id = "";

    HashMap<String, String> headers = new HashMap<String, String>();
    HashMap<String, String> parameters = new HashMap<String, String>();


     public FileTransferSettings(String jsonSettings) throws Exception {
        try {
            JSONObject settings = new JSONObject(jsonSettings);

            filePath = settings.getString("filePath");
            serverUrl = settings.getString("serverUrl");
            id = settings.getString("id");

            if (settings.has("headers")) {
                JSONObject headersObject = settings.getJSONObject("headers");
                if (headersObject != null) {

                    Iterator<?> keys = headersObject.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        String value = headersObject.getString(key);
                        headers.put(key, value);
                    }

                }
            }

            if (settings.has("parameters")) {
                JSONObject parametersObject = settings.getJSONObject("parameters");
                if (parametersObject != null) {

                    Iterator<?> keys = parametersObject.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        String value = parametersObject.getString(key);
                        parameters.put(key, value);
                    }

                }
            }

            if (!new File(filePath).exists())
                throw new IOException("File not found: " + filePath);

        } catch (Exception e) {
            throw e;
        }
    }



}
