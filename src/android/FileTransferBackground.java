package com.spoon.backgroundFileUpload;

import android.content.Context;
import android.util.Log;

import com.sromku.simple.storage.SimpleStorage;
import com.sromku.simple.storage.Storage;
import com.sromku.simple.storage.helpers.OrderType;

import net.gotev.uploadservice.BinaryUploadRequest;
import net.gotev.uploadservice.HttpUploadRequest;
import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.UploadStatusDelegate;
import net.gotev.uploadservice.okhttp.OkHttpStack;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileTransferBackground extends CordovaPlugin {

  private final String uploadDirectoryName = "FileTransferBackground";
  private Storage storage;
  private CallbackContext uploadCallback;
  private NetworkMonitor networkMonitor;

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {

    try {
      if (action.equalsIgnoreCase("initManager")) {
        this.initManager(args.length() > 0 ? args.get(0).toString() : null, callbackContext);
      } else if (action.equalsIgnoreCase("removeUpload")) {
        this.removeUpload(args.length() > 0 ? args.get(0).toString() : null, callbackContext);
      } else {
        uploadCallback = callbackContext;
        upload(args.length() > 0 ? (JSONObject) args.get(0) : null, uploadCallback);
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

  private void upload(JSONObject jsonPayload, final CallbackContext callbackContext) throws Exception {
    final FileTransferSettings payload = new FileTransferSettings(jsonPayload.toString());
    this.createUploadInfoFile(payload.id, jsonPayload);
    String method = jsonPayload.optString("method", "POST");
    Boolean multipart = jsonPayload.optBoolean("multipart", false);
    final FileTransferBackground self = this;

    if (NetworkMonitor.isConnected) {

      HttpUploadRequest request;

      if (multipart) {
        request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), payload.id, payload.serverUrl).addFileToUpload(payload.filePath, payload.fileKey);

      } else {
        request = new BinaryUploadRequest(this.cordova.getActivity().getApplicationContext(), payload.id, payload.serverUrl).setFileToUpload(payload.filePath);
      }

      request.setMethod(method)
              .setMaxRetries(0)
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
                    if (callbackContext != null && self.webView != null)
                      callbackContext.sendPluginResult(progressUpdate);
                  } catch (Exception e) {
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
                    errorObj.put("error", "execute failed");
                    errorObj.put("state", "FAILED");
                    PluginResult errorResult = new PluginResult(PluginResult.Status.ERROR, errorObj);
                    errorResult.setKeepCallback(true);
                    if (callbackContext != null && self.webView != null)
                      callbackContext.sendPluginResult(errorResult);

                  } catch (Exception e) {
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
                    objResult.put("statusCode", serverResponse.getHttpCode());
                    PluginResult completedUpdate = new PluginResult(PluginResult.Status.OK, objResult);
                    completedUpdate.setKeepCallback(true);
                    if (callbackContext != null && self.webView != null)
                      callbackContext.sendPluginResult(completedUpdate);
                  } catch (Exception e) {
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

    } else {
      LogMessage("Upload failed. Image added to pending list");
      updateStateForUpload(payload.id, UploadState.FAILED, null);
    }
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

  private void createUploadInfoFile(String fileId, JSONObject upload) {
    try {
      upload.put("createdDate", System.currentTimeMillis() / 1000);
      upload.put("state", UploadState.STARTED);

      storage.createFile(uploadDirectoryName, fileId + ".json", upload.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void updateStateForUpload(String fileId, String state, String serverResponse) {
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

  private void removeUploadInfoFile(String fileId) {
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
      UploadService.UPLOAD_POOL_SIZE = 1;

      storage = SimpleStorage.getInternalStorage(this.cordova.getActivity().getApplicationContext());
      storage.createDirectory(uploadDirectoryName);
      LogMessage("created working directory ");

      networkMonitor = new NetworkMonitor(webView.getContext(), new ConnectionStatusListener() {
        @Override
        public void connectionDidChange(Boolean isConnected, String networkType) {
          LogMessage("Connection change, Connected:" + isConnected);
          uploadPendingList();
        }
      });

      if (options != null) {
        //initialised global configuration parameters here
        //JSONObject settings = new JSONObject(options);
      }

      ArrayList<JSONObject> previousUploads = getUploadHistory();
      for (JSONObject upload : previousUploads) {
        String state = upload.getString("state");
        String id = upload.getString("id");

        if (state.equalsIgnoreCase(UploadState.UPLOADED)) {

          JSONObject objResult = new JSONObject();
          objResult.put("id", id);
          objResult.put("completed", true);
          objResult.put("serverResponse", upload.getString("serverResponse"));
          PluginResult progressUpdate = new PluginResult(PluginResult.Status.OK, objResult);
          progressUpdate.setKeepCallback(true);
          callbackContext.sendPluginResult(progressUpdate);

        } else if (state.equalsIgnoreCase(UploadState.FAILED) || state.equalsIgnoreCase(UploadState.STARTED)) {
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

  private void uploadPendingList() {
    ArrayList<JSONObject> previousUploads = getUploadHistory();
    for (JSONObject upload : previousUploads) {
      try {
        String state = upload.getString("state");
        if (state.equalsIgnoreCase(UploadState.FAILED) || state.equalsIgnoreCase(UploadState.STARTED)) {
          this.upload(upload, uploadCallback);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void onDestroy() {
    Log.d("FileTransferBackground", " FileTransferBackground onDestroy");
    if (networkMonitor != null)
      networkMonitor.stopMonitoring();
  }
}
