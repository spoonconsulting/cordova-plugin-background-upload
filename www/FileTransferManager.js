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

var FileTransferManager = function(options) {
    this._handlers = {
        'resume': [],
        'progress': [],
        'success': [],
        'error': []
    };

    // require options parameter
    if (typeof options === 'undefined') {
        throw new Error('The options argument is required.');
    }

    // store the options to this object instance
    this.options = options;

    var that = this;
    this.options.success = function(result) {

        // success callback is used to both report operation progress and
        // as operation completeness handler
        if (result && typeof result.completed != 'undefined') {
            that.emit('success', result);
        } else if (result && typeof result.progress != 'undefined') {
            that.emit('progress', result);
        } else if (result && typeof result.resuming) {
            that.emit('resume', result);
        } else {
            that.emit('success', result);
        }

    };

    // triggered on error
    this.options.fail = function(msg) {
        var e = (typeof msg === 'string') ? new Error(msg) : msg;
        that.emit('error', e);
    };


    exec(success, fail, 'FileTransferManager', 'initManager', []);

};

module.exports = {

    init: function(options) {
        return new FileTransferManager(options ? options : {});
    },

    upload: function(payload, callback) {

        if (payload == null) {
            return callback(new Error("upload settings object is missing or invalid argument"));
        }

        if (payload.serverUrl == null) {
            return callback(new Error("server url is required"));

        }

        if (payload.serverUrl.trim() == '') {
            return callback(new Error("invalid server url"));
        }

        if (!payload.filePath) {
            return callback(new Error("filePath is required"));
        }

        if (!this.options) {
            return callback(new Error("FileTransferManager not properly initialised. Call FileTransferManager.init(options) first"));
        }

        //remove the prefix for mobile urls
        payload.filePath = payload.filePath.replace('file://', '');

        exec(this.options.success, this.options.fail, "FileTransferBackground", "startUpload", [payload]);

    },

    /**
     * FileTransferManager Object.
     *
     * Expose the FileTransferManager object for direct use
     * and testing. Typically, you should use the
     * .init helper method.
     */

    FileTransferManager: FileTransferManager
};

/*
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

    if (!payload.filePath) {
      errorCallback("filePath is required");
      return deferral.promise;
    }

    //remove the prefix for mobile urls
    payload.filePath = payload.filePath.replace('file://','');

    exec(successCallback, errorCallback, "FileTransferBackground", "startUpload", [payload]);

    // custom mechanism to trigger stop when user cancels pending operation
    deferral.promise.onCancelled = function () {
      me.stop();
    };

    return deferral.promise;
  };


  module.exports = FileTransferManager;
  */