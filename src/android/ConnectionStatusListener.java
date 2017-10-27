package com.spoon.backgroundFileUpload;

/**
 * Created by Muzammil on 24/10/2017.
 */

interface ConnectionStatusListener {
  void connectionDidChange(Boolean isConnected, String networkType);
}
