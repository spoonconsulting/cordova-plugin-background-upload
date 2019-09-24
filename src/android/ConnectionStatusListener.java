package com.spoon.backgroundfileupload;

interface ConnectionStatusListener {
  void connectionDidChange(Boolean isConnected, String networkType);
}
