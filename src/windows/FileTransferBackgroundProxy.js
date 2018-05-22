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

var appData = Windows.Storage.ApplicationData.current;

module.exports = {
    initManager: function(successCallback, errorCallback, args) {
        var onSuccess = function (uploads) {
            for (var i = 0; i < uploads.length; i++) {
                var transfer = new FileTransfer(successCallback, errorCallback);
                transfer.resume(uploads[i]);
            }
        };
        Windows.Networking.BackgroundTransfer.BackgroundUploader.getCurrentUploadsAsync().done(onSuccess);
    },

    startUpload: function(successCallback, errorCallback, args) {
        var payload = args[0];
        var uploadUrl = payload.serverUrl;
        var fileKey = payload.fileKey;
        var filePath = payload.filePath;
        var headers = payload.headers || {};
        var parameters = payload.parameters || {};
        var fileId = payload.id;
        var transfer = new FileTransfer(successCallback, errorCallback);

        if (typeof(uploadUrl) !== 'string') {
            transfer.onError(new Error('invalid url'));
            return;
        }

        if (typeof(filePath) !== 'string') {
            transfer.onError(new Error('file path is required'));
            return;
        }

        if (filePath.indexOf('ms-appdata:///') === 0) {
            // Handle 'ms-appdata' scheme
            filePath = filePath.replace('ms-appdata:///local', appData.localFolder.path)
                               .replace('ms-appdata:///temp', appData.temporaryFolder.path);
        } else if (filePath.indexOf('cdvfile://') === 0) {
            filePath = filePath.replace('cdvfile://localhost/persistent', appData.localFolder.path)
                               .replace('cdvfile://localhost/temporary', appData.temporaryFolder.path);
        } else {
            filePath = appData.localFolder.path + filePath.split('/').join('\\');
        }
        // normalize path separators
        filePath = cordovaPathToNative(filePath);

        Windows.Storage.StorageFile.getFileFromPathAsync(filePath).then(function (storageFile) {
            transfer.start(fileId);
            transfer.addHeaders(headers);
            transfer.addParameters(parameters);
            transfer.addFile(fileKey, storageFile);
            transfer.send(uploadUrl);
        }, function (err) {
            transfer.onError(err);
        });
    },

    removeUpload: function(successCallback, errorCallback, args) {
        var id = args[0];
        var transfer = findTransfer(id);
        if (transfer) {
            transfer.cancel();
        }
        successCallback();
    },
};

function FileTransfer(successCallback, errorCallback) {
    this.id = null;
    this.uploader = null;
    this.upload = null;
    this.promise = null;
    this.parts = [];

    var _this = this;
    this.onSuccess = function () {
        _this.getResponseText().then(function (responseText) {
            var msg = {
                id: _this.id,
                completed: true,
                serverResponse: responseText,
            };
            successCallback(msg, { keepCallback: true });
            forgetTransfer(transfer);
        });
    };
    this.onError = function (err) {
        _this.getResponseText(upload).then(function (responseText) {
            var msg = {
                id: _this.id,
                error: responseText || err.message,
                state: getUploadStatus(_this.upload),
            };
            errorCallback(msg, { keepCallback: true });
            forgetTransfer(transfer);
        });
    };
    this.onProgress = function () {
        var loaded = _this.upload.progress.bytesSent;
        var total = _this.upload.progress.totalBytesToSend;
        var msg = {
            id: _this.id,
            progress: Math.floor(loaded / total * 100),
            state: getUploadStatus(_this.upload),
        };
        successCallback(msg, { keepCallback: true });
    };
}

FileTransfer.prototype.start = function(id) {
    this.id = id;
    this.uploader = new Windows.Networking.BackgroundTransfer.BackgroundUploader();
};

FileTransfer.prototype.addHeaders = function(headers) {
    for (var header in headers) {
        if (headers.hasOwnProperty(header)) {
            this.uploader.setRequestHeader(header, headers[header]);
        }
    }
};

FileTransfer.prototype.addParameters = function(parameters) {
    // create content part for params only if value is specified because CreateUploadAsync fails otherwise
    for (var key in parameters) {
        if (parameters.hasOwnProperty(key)) {
            var value = parameters[key];
            if (value) {
                var contentPart = new Windows.Networking.BackgroundTransfer.BackgroundTransferContentPart(key);
                contentPart.setText(value);
                this.parts.push(contentPart);
            }
        }
    }
};

FileTransfer.prototype.addFile = function(fileKey, storageFile) {
    var contentPart = new Windows.Networking.BackgroundTransfer.BackgroundTransferContentPart(fileKey);
    contentPart.setFile(storageFile);
    this.parts.push(contentPart);
};

FileTransfer.prototype.send = function(uploadUrl) {
    try {
        var uri = new Windows.Foundation.Uri(uploadUrl);
        var _this = this;
        this.uploader.createUploadAsync(uri, this.parts).then(function (upload) {
            _this.upload = upload;
            _this.promise = _this.upload.startAsync().then(_this.onSuccess, _this.onError, _this.onProgress);
            rememberTransfer(_this);
        }, function (err) {
            _this.onError(err);
        });
    } catch (e) {
        this.onError(new Error('invalid url'));
    }
};

FileTransfer.prototype.resume = function(upload) {
    this.upload = upload;
    this.id = findTransferId(upload);
    if (this.id) {
        var status = getUploadStatus(upload);
        if (status === 'UPLOADED') {
            this.onSuccess();
        } else if (status === 'FAILED') {
            this.onError(new Error('background transfer error'));
        } else if (status === 'UPLOADING' || status === 'PAUSED') {
            this.promise = this.upload.attachAsync().then(this.onSuccess, this.onError, this.onProgress);
            this.onProgress();
            rememberTransfer(this);
        }
    }
};

FileTransfer.prototype.cancel = function() {
    if (this.promise) {
        this.promise.cancel();
    }
    forgetTransfer(transfer);
};

FileTransfer.prototype.getResponseText = function() {
    var response = this.upload.getResponseInformation();
    var received = this.upload.progress.bytesReceived;
    if (response) {
        if (received > 0) {
            var stream = this.upload.getResultStreamAt(0);
            var reader = new Windows.Storage.Streams.DataReader(stream);
            return reader.loadAsync(received).then(function (size) {
                var responseText = reader.readString(size);
                reader.close();
                return responseText;
            }, function (err) {
                return '';
            });
        } else {
            return WinJS.Promise.wrap('');
        }
    } else {
        return WinJS.Promise.wrap('connection error');
    }
}

var transfers = {};
var transferIds;
var transferIdsKey = 'backgroundTransferFileIds';

function rememberTransfer(transfer) {
    transfers[transfer.id] = transfer;
    if (transfer.upload)  {
        loadTransferIds();
        transferIds[transfer.upload.guid] = transfer.id;
        saveTransferIds();
    }
}

function forgetTransfer(transfer) {
    delete transfers[transfer.id];
    if (transfer.upload) {
        loadTransferIds();
        delete transferIds[transfer.upload.guid];
        saveTransferIds();
    }
}

function findTransfer(id) {
    return transfers[id] || null;
}

function findTransferId(upload) {
    loadTransferIds();
    return transferIds[upload.guid];
}

function loadTransferIds() {
    if (!transferIds) {
        transferIds = appData.localSettings[transferIdsKey];
        if (!transferIds) {
            transferIds = {};
        }
    }
}

function saveTransferIds() {
    var keys = Object.keys(transferIds);
    if (keys.length > 0) {
        appData.localSettings[transferIdsKey] = transferIds;
    } else {
        delete appData.localSettings[transferIdsKey];
    }
}

function getUploadStatus(upload) {
    switch (upload.progress.status) {
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.completed:
            return 'UPLOADED';
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.error:
            return 'FAILED';
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.canceled:
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.idle:
            return 'STOPPED';
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.pausedByApplication:
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.pausedCostedNetwork:
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.pausedNoNetwork:
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.pausedRecoverableWebErrorStatus:
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.pausedSystemPolicy:
            return 'PAUSED';
        case Windows.Networking.BackgroundTransfer.BackgroundTransferStatus.running:
            return 'UPLOADING';
    }
}

function cordovaPathToNative(path) {

    var cleanPath = String(path);
    // turn / into \\
    cleanPath = cleanPath.replace(/\//g, '\\');
    // turn  \\ into \
    cleanPath = cleanPath.replace(/\\\\/g, '\\');
    // strip end \\ characters
    cleanPath = cleanPath.replace(/\\+$/g, '');
    return cleanPath;
}

require("cordova/exec/proxy").add("FileTransferBackground", module.exports);
