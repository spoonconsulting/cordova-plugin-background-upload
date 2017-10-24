package com.spoon.backgroundFileUpload;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;


/**
 * Created by Muzammil on 24/10/2017.
 */

public class Connection extends BroadcastReceiver {

  public static boolean isConnected = false;
  private static ConnectionStatusListener delegate;

  public static void init(Context context) {
    isConnected = isConnectedToNetwork(context);
  }

  public static void setDelegate(ConnectionStatusListener parent) {
    delegate = parent;
  }

  public static boolean isConnectedToNetwork(Context context) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    isConnected = isConnectedToNetwork(context);

    if (delegate != null)
      delegate.connectionDidChange();
  }
}
