package com.spoon.backgroundFileUpload;

interface ConnectionStatusListener {
  void connectionDidChange(Boolean isConnected, String networkType);
}
