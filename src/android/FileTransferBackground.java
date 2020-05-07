package com.spoon.backgroundfileupload;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

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
        if (action.equalsIgnoreCase("initManager")) {
            this.initManager(args.get(0).toString(), callbackContext);
            return true;
        }
        if (action.equalsIgnoreCase("destroy")) {
            this.destroy();
            callbackContext.success();
            return true;
        }
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    if (action.equalsIgnoreCase("removeUpload")) {
                        managerService.removeUpload(args.get(0).toString());
                    } else if (action.equalsIgnoreCase("acknowledgeEvent")) {
                        managerService.acknowledgeEvent(args.getString(0));
                    } else if (action.equalsIgnoreCase("startUpload")) {
                        managerService.addUpload((JSONObject) args.get(0));
                    }
                    callbackContext.success();
                } catch (Exception exception) {
                    String message = "(" + exception.getClass().getSimpleName() + ") - " + exception.getMessage();
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, message);
                    callbackContext.sendPluginResult(result);
                    exception.printStackTrace();
                }
            }
        });
        return true;
    }

    private void initManager(String options, final CallbackContext callbackContext) throws IllegalStateException {
        if (managerService != null) { // a faire avec une autre variable !
            throw new IllegalStateException("initManager was called twice");
        }

        this.uploadCallback = callbackContext;
        Intent intent = new Intent(cordova.getContext(), ManagerService.class);
        intent.putExtra("options", options);
           
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cordova.getActivity().startForegroundService(intent);
        } else {
            cordova.getActivity().startService(intent);
        }
        cordova.getActivity().bindService(intent, this, Context.BIND_AUTO_CREATE);
        // ManagerService.logMessage("Service running"); pas sur manager service
    }

    public void onDestroy() {
        //ManagerService.logMessage("eventLabel='Uploader plugin onDestroy'"); pas sur manager service
        destroy();
    }

    public void destroy() {
        his.managerService.setPlugin(null);
        cordova.getActivity().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        ManagerService.LocalBinder binder = (ManagerService.LocalBinder) iBinder;
        this.managerService = binder.getServiceInstance();
        this.managerService.setPlugin(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
    }
    
    @Override
    public void serviceError(obj) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
        result.setKeepCallback(true);

        this.uploadCallback.sendPluginResult(result);
    }
    
    @Override
    public void sendEvent(obj) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
        result.setKeepCallback(true);

        this.uploadCallback.sendPluginResult(result);
    }
}
