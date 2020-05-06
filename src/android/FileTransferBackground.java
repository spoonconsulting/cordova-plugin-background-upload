package com.spoon.backgroundfileupload;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import net.gotev.uploadservice.UploadServiceConfig;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FileTransferBackground extends CordovaPlugin implements ServiceConnection, ManagerService.ICallback {
    private CallbackContext uploadCallback;
    private ManagerService managerService;

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
                        managerService.removeUpload(args.get(0).toString(), callbackContext);
                    } else if (action.equalsIgnoreCase("acknowledgeEvent")) {
                        managerService.acknowledgeEvent(args.getString(0), callbackContext);
                    } else if (action.equalsIgnoreCase("startUpload")) {
                        managerService.addUpload((JSONObject) args.get(0));
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
        if (managerService != null) {
            throw new IllegalStateException("initManager was called twice");
        }

        this.uploadCallback = callbackContext;

        if (!isServiceRunning(ManagerService.class)) {
            Intent intent = new Intent(cordova.getContext(), ManagerService.class);
            intent.putExtra("options", options);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cordova.getActivity().startForegroundService(intent);
                cordova.getActivity().bindService(intent, this, Context.BIND_AUTO_CREATE);
            } else {
                cordova.getActivity().startService(intent);
                cordova.getActivity().bindService(intent, this, Context.BIND_AUTO_CREATE);
            }
        } else {
            Intent intent = new Intent(cordova.getContext(), ManagerService.class);
            cordova.getActivity().bindService(intent, this, Context.BIND_AUTO_CREATE);

            UploadServiceConfig.initialize(
                    cordova.getActivity().getApplication(),
                    "com.spoon.backgroundfileupload.channel",
                    false
            );

            this.managerService.setReady(true);

            ManagerService.logMessage("Service running");
        }
    }

    public void onDestroy() {
        ManagerService.logMessage("eventLabel='Uploader plugin onDestroy'");
        destroy();
    }

    public void destroy() {
        this.managerService.setReady(false);
        this.managerService.stopServiceIfComplete();
        cordova.getActivity().unbindService(this);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) cordova.getActivity().getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        ManagerService.LocalBinder binder = (ManagerService.LocalBinder) iBinder;
        this.managerService = binder.getServiceInstance();
        this.managerService.setReady(true);
        this.managerService.setCallback(this);

        this.managerService.sendMissingEvents(cordova.getActivity());
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        ManagerService.logMessage("Service disconnected");
        this.managerService.setReady(false);
    }

    @Override
    public void sendPluginResult(PluginResult result) {
        this.uploadCallback.sendPluginResult(result);
    }
}
