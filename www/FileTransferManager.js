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

var FileTransferManager = function (options) {
    this._handlers = {

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
    this.options.success = function (result) {

        // success callback is used to both report operation progress and
        // as operation completeness handler
        //console.log(JSON.stringify(result));

        if (result && typeof result.completed != 'undefined') {
            that.emit('success', result);
        }
        // else if (result && typeof result.progress != 'undefined') {
        //     that.emit('progress', result);
        // } 
        else {
            that.emit('progress', result);
        }

    };

    // triggered on error
    this.options.fail = function (msg) {
        var e = (typeof msg === 'string') ? new Error(msg) : msg;
        that.emit('error', e);
    };


    exec(this.options.success, this.options.fail, 'FileTransferBackground', 'initManager', []);

};

FileTransferManager.prototype.startUpload = function (payload) {

    if (payload == null) {
        this.options.fail(
            "upload settings object is missing or invalid argument"
        );
        return;
    }
    if (payload.serverUrl == null) {
        this.options.fail(
            "server url is required"
        );
        return;

    }

    if (payload.serverUrl.trim() == '') {
        this.options.fail(
            "invalid server url"
        );
        return;
    }

    if (!payload.filePath) {
        this.options.fail(
            "filePath is required"
        );
        return;
    }

    if (!this.options) {
        console.error("FileTransferManager not properly initialised. Call FileTransferManager.init(options) first");
        return;
    }

    //remove the prefix for mobile urls
    payload.filePath = payload.filePath.replace('file://', '');

    exec(null, null, "FileTransferBackground", "startUpload", [payload]);

};


/**
 * Listen for an event.
 *
 * Any event is supported, but the following are built-in:
 *
 *   - registration
 *   - notification
 *   - error
 *
 * @param {String} eventName to subscribe to.
 * @param {Function} callback triggered on the event.
 */

FileTransferManager.prototype.on = function (eventName, callback) {
    if (!this._handlers.hasOwnProperty(eventName)) {
        this._handlers[eventName] = [];
    }
    this._handlers[eventName].push(callback);
};

/**
 * Remove event listener.
 *
 * @param {String} eventName to match subscription.
 * @param {Function} handle function associated with event.
 */

FileTransferManager.prototype.off = function (eventName, handle) {
    if (this._handlers.hasOwnProperty(eventName)) {
        var handleIndex = this._handlers[eventName].indexOf(handle);
        if (handleIndex >= 0) {
            this._handlers[eventName].splice(handleIndex, 1);
        }
    }
};

/**
 * Emit an event.
 *
 * This is intended for internal use only.
 *
 * @param {String} eventName is the event to trigger.
 * @param {*} all arguments are passed to the event listeners.
 *
 * @return {Boolean} is true when the event is triggered otherwise false.
 */

FileTransferManager.prototype.emit = function () {
    var args = Array.prototype.slice.call(arguments);
    var eventName = args.shift();

    if (!this._handlers.hasOwnProperty(eventName)) {
        return false;
    }

    for (var i = 0, length = this._handlers[eventName].length; i < length; i++) {
        var callback = this._handlers[eventName][i];
        if (typeof callback === 'function') {
            callback.apply(undefined, args);
        } else {
            console.log('event handler: ' + eventName + ' must be a function');
        }
    }

    return true;
};





module.exports = {

    init: function (options) {
        return new FileTransferManager(options ? options : {});
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