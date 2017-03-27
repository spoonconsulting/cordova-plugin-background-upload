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
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.ionicframework.myapp736094.BuildConfig;

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
import java.util.HashMap;
import java.util.Iterator;

public class FileTransferBackground extends CordovaPlugin {


  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    try {

      UploadService.HTTP_STACK = new OkHttpStack();


      FileTransferSettings payload = new FileTransferSettings((args.get(0)).toString());

      MultipartUploadRequest request = new MultipartUploadRequest(this.cordova.getActivity().getApplicationContext(), payload.serverUrl)//"https://api-de.cloudinary.com/v1_1/hclcistqq/auto/upload")
          .addFileToUpload(payload.filePath, "file")
          .setMaxRetries(2)
          .setDelegate(new UploadStatusDelegate() {
            @Override
            public void onProgress(Context context, UploadInfo uploadInfo) {
              LogMessage("progress: " + uploadInfo.getProgressPercent());

              try {
                JSONObject objResult = new JSONObject();
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
              callbackContext.error(exception.toString());
            }

            @Override
            public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {

              LogMessage("App onCompleted");
              LogMessage("server response : " + serverResponse.getBodyAsString());
              callbackContext.success(serverResponse.getBodyAsString());
            }

            @Override
            public void onCancelled(Context context, UploadInfo uploadInfo) {
              LogMessage("App cancel");
            }
          });

      for (String key: payload.parameters.keySet()) {
        request.addParameter(key, payload.parameters.get(key));
      }

      for (String key: payload.headers.keySet()) {
        request.addHeader(key, payload.headers.get(key));
      }
      // String uploadId =
      request.startUpload();


    } catch (Exception ex) {
      callbackContext.error(ex.getMessage());
    }
    return true;
  }

  private void LogMessage(String message) {

    Log.d("FileTransferBG", message);
  }


}


class FileTransferSettings {

  String fileName = "";
  String filePath = "";
  String serverUrl = "";

  HashMap<String, String> headers = new HashMap<String, String>();
  HashMap<String, String> parameters = new HashMap<String, String>();



  public FileTransferSettings(String jsonSettings) throws Exception {
    try {
      JSONObject settings = new JSONObject(jsonSettings);

      fileName = settings.getString("fileName");
      filePath = settings.getString("filePath");
      serverUrl = settings.getString("serverUrl");

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
