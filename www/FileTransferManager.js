/*
  /*
  *
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied.  See the License for the
  * specific language governing permissions and limitations
  * under the License.
  *
  */
  var exec = require('cordova/exec');
  var Promise = require('./Promise');

  /**
   * Initializes a new instance of FileTransferManager object.
   * Used to configure upload prior to the actual creation of the upload operation.
   */
  var FileTransferManager = function () {

  };

  /**
   * Initializes an upload Operation object that contains the specified Uri.
   *
   * @param {Any} payload The settings for the upload
   */
  FileTransferManager.prototype.upload = function (payload) {

    var deferral = new Promise.Deferral(),
      me = this,
      successCallback = function (result) {

        // success callback is used to both report operation progress and
        // as operation completeness handler
        if (result && typeof result.completed != 'undefined') {
          deferral.notify(result.completed);
        } else if (result && typeof result.progress != 'undefined') {
          deferral.notify(result.progress);
        } else {
          deferral.resolve(result);
        }
      },
      errorCallback = function (err) {
        deferral.reject(err);
      };


    if (payload == null) {
      errorCallback("upload settings object is missing or invalid argument");
      return deferral.promise;
    }

    if (payload.serverUrl == null) {
      errorCallback("server url is required");
      return deferral.promise;
    }

    if (payload.serverUrl.trim() == '') {
      errorCallback("invalid server url");
      return deferral.promise;
    }

    if (!isOnDevice() && payload.file == null) {
      errorCallback("file is required");
      return deferral.promise;
    }


    exec(successCallback, errorCallback, "FileTransferBackground", "startUpload", [isOnDevice() ? JSON.stringify(payload) : payload]);

    // custom mechanism to trigger stop when user cancels pending operation
    deferral.promise.onCancelled = function () {
      me.stop();
    };

    return deferral.promise;
  };


  function isOnDevice() {
    //return (window.cordova || window.PhoneGap || window.phonegap) 
    //&& /^file:\/{3}[^\/]/i.test(window.location.href) 
    // && 
    return /ios|iphone|ipod|ipad|android/i.test(navigator.userAgent);
  }


  module.exports = FileTransferManager;