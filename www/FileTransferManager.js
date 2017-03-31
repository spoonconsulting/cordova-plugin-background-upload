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
 * @param {string} uri The location of the resource.
 * @param {File} resultFile The file that the response will be written to.
 */
FileTransferManager.prototype.upload = function (uploadSettings) {

  if (uploadSettings == null) {
    throw new Error("uploadSettings object is missing or invalid argument");
  }

  if (isOnDevice() ) {
    console.log("Running on device!");
} else {
     console.log("running on browser!");
}
//console.log(require('superagent'))
//return;

  this.uploadSettings = uploadSettings;

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
  var settingsString = JSON.stringify(this.uploadSettings);
  exec(successCallback, errorCallback, "FileTransferBackground", "startUpload", [isOnDevice() ? settingsString: this.uploadSettings]);

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
