package com.spoon.backgroundFileUpload;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;


import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.UploadStatusDelegate;
import net.gotev.uploadservice.okhttp.OkHttpStack;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;

public class FileTransferBackground extends CordovaPlugin {


    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        try {

            if (action.equalsIgnoreCase("initManager")) {
                this.initManager(args.length() > 0 ? args.get(0).toString(): null);
            } else {

                final FileTransferSettings payload = new FileTransferSettings((args.get(0)).toString());

                String fileName=payload.filePath.substring(payload.filePath.lastIndexOf("/")+1);

                UploadNotificationConfig notificationConfig = new UploadNotificationConfig()
                        .setTitle("Uploading "+ fileName)
                        .setInProgressMessage("Uploading at [[UPLOAD_RATE]] ([[PROGRESS]])")
                        .setErrorMessage("Error while uploading")
                        .setCompletedMessage("Successfully uploaded ");

                MultipartUploadRequest request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), payload.serverUrl)
                        .addFileToUpload(payload.filePath, "file")
                        .setNotificationConfig(notificationConfig)
                        .setDelegate(new UploadStatusDelegate() {
                            @Override
                            public void onProgress(Context context, UploadInfo uploadInfo) {
                                LogMessage("id:" + payload.id + " progress: " + uploadInfo.getProgressPercent());

                                try {
                                    JSONObject objResult = new JSONObject();
                                    objResult.put("id", payload.id);
                                    objResult.put("progress", uploadInfo.getProgressPercent());
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
                                    JSONObject errorObj = new JSONObject();
                                    errorObj.put("id", payload.id);
                                    errorObj.put("error", "upload failed");
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

                                    JSONObject objResult = new JSONObject();
                                    objResult.put("id", payload.id);
                                    objResult.put("completed", true);
                                    objResult.put("serverResponse", serverResponse.getBodyAsString());
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

    private void initManager(String options) {
        UploadService.HTTP_STACK = new OkHttpStack();
        if (options != null) {
            //initialised global configuration parameters here
            //JSONObject settings = new JSONObject(options);
        }
    }

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

            JSONObject headersObject = settings.getJSONObject("headers");
            if (headersObject != null) {

                Iterator<?> keys = headersObject.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String value = headersObject.getString(key);
                    headers.put(key, value);
                }

            }


            JSONObject parametersObject = settings.getJSONObject("parameters");
            if (parametersObject != null) {

                Iterator<?> keys = parametersObject.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String value = parametersObject.getString(key);
                    parameters.put(key, value);
                }

            }

            if (!new File(filePath).exists())
                throw new IOException("File not found: " + filePath);

        } catch (Exception e) {
            throw e;
        }
    }


}
