/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package com.spoon.backgroundFileUpload;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.Locale;

public class NetworkMonitor {

  public static final String WIFI = "wifi";
  public static final String WIMAX = "wimax";
  // mobile
  public static final String MOBILE = "mobile";
  // Android L calls this Cellular, because I have no idea!
  public static final String CELLULAR = "cellular";
  // 2G network types
  public static final String TWO_G = "2g";
  public static final String GSM = "gsm";
  public static final String GPRS = "gprs";
  public static final String EDGE = "edge";
  // 3G network types
  public static final String THREE_G = "3g";
  public static final String CDMA = "cdma";
  public static final String UMTS = "umts";
  public static final String HSPA = "hspa";
  public static final String HSUPA = "hsupa";
  public static final String HSDPA = "hsdpa";
  public static final String ONEXRTT = "1xrtt";
  public static final String EHRPD = "ehrpd";
  // 4G network types
  public static final String FOUR_G = "4g";
  public static final String LTE = "lte";
  public static final String UMB = "umb";
  public static final String HSPA_PLUS = "hspa+";
  // return type
  public static final String TYPE_UNKNOWN = "unknown";
  public static final String TYPE_ETHERNET = "ethernet";
  public static final String TYPE_ETHERNET_SHORT = "eth";
  public static final String TYPE_WIFI = "wifi";
  public static final String TYPE_2G = "2g";
  public static final String TYPE_3G = "3g";
  public static final String TYPE_4G = "4g";
  public static final String TYPE_NONE = "none";
  static boolean isConnected = false;
  private ConnectionStatusListener delegate;
  private BroadcastReceiver receiver;

  NetworkMonitor(Context context, ConnectionStatusListener parent) {
    delegate = parent;
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    isConnected = isConnectedToNetwork(context);

    if (this.receiver == null) {
      this.receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          NetworkInfo activeNetwork = getNetworkInfo(context);
          isConnected = isConnectedToNetwork(context);

          if (delegate != null)
            delegate.connectionDidChange(isConnected, getType(activeNetwork));
        }

      };
      context.registerReceiver(receiver, intentFilter);
    }
  }

  private static NetworkInfo getNetworkInfo(Context context) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (cm != null) {
      return cm.getActiveNetworkInfo();
    }
    return null;
  }

  private static boolean isConnectedToNetwork(Context context) {
    NetworkInfo activeNetwork = getNetworkInfo(context);
    return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
  }

  private String getType(NetworkInfo info) {
    if (info != null) {
      String type = info.getTypeName().toLowerCase(Locale.US);

      if (type.equals(WIFI)) {
        return TYPE_WIFI;
      } else if (type.toLowerCase().equals(TYPE_ETHERNET) || type.toLowerCase().startsWith(TYPE_ETHERNET_SHORT)) {
        return TYPE_ETHERNET;
      } else if (type.equals(MOBILE) || type.equals(CELLULAR)) {
        type = info.getSubtypeName().toLowerCase(Locale.US);
        if (type.equals(GSM) ||
          type.equals(GPRS) ||
          type.equals(EDGE) ||
          type.equals(TWO_G)) {
          return TYPE_2G;
        } else if (type.startsWith(CDMA) ||
          type.equals(UMTS) ||
          type.equals(ONEXRTT) ||
          type.equals(EHRPD) ||
          type.equals(HSUPA) ||
          type.equals(HSDPA) ||
          type.equals(HSPA) ||
          type.equals(THREE_G)) {
          return TYPE_3G;
        } else if (type.equals(LTE) ||
          type.equals(UMB) ||
          type.equals(HSPA_PLUS) ||
          type.equals(FOUR_G)) {
          return TYPE_4G;
        }
      }
    } else {
      return TYPE_NONE;
    }
    return TYPE_UNKNOWN;
  }

  void stopMonitoring() {
    if (this.receiver != null) {
      try {
        receiver = null;
      } catch (Exception e) {
        e.printStackTrace();

      }
    }
  }
}

