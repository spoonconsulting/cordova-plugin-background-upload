var exec = require('cordova/exec')

var FileTransferManager = function (options, callback, ready) {
  options.parallelUploadsLimit = options.parallelUploadsLimit || 1
  this.options = options

  if (typeof callback !== 'function') {
    throw new Error('event handler must be a function')
  }
  if (typeof ready !== 'function') {
    throw new Error('ready handler must be a function')
  }

  this.callback = callback
  exec(function(event) {
    if(event.ready == true) {
      ready();
      return;
    }
    callback(event)
  }, null, 'FileTransferBackground', 'initManager', [this.options])
}

FileTransferManager.prototype.startUpload = function (payload) {
  if (!payload) {
    return this.callback({ state: 'FAILED', error: 'upload settings object is missing or invalid argument' })
  }

  if (!payload.id) {
    return this.callback({ state: 'FAILED', error: 'upload id is required' })
  }

  if (!payload.serverUrl) {
    return this.callback({ id: payload.id, state: 'FAILED', error: 'server url is required' })
  }

  if (payload.serverUrl.trim() === '') {
    return this.callback({ id: payload.id, state: 'FAILED', error: 'invalid server url' })
  }

  if (!payload.filePath) {
    return this.callback({ id: payload.id, state: 'FAILED', error: 'filePath is required' })
  }

  payload.fileKey = payload.fileKey || 'file'
  payload.notificationTitle = payload.notificationTitle || 'Uploading files'
  payload.headers = payload.headers || {}
  payload.parameters = payload.parameters || {}

  var self = this
  window.resolveLocalFileSystemURL(payload.filePath, function (entry) {
    payload.filePath = entry.toURL().replace('file://', '')
    exec(self.callback, null, 'FileTransferBackground', 'startUpload', [payload])
  }, function () {
    self.callback({ id: payload.id, state: 'FAILED', error: 'File not found: ' + payload.filePath })
  })
}

FileTransferManager.prototype.removeUpload = function (id, successCb, errorCb) {
  if (!id) {
    if (errorCb) {
      errorCb({ error: 'upload id is required' })
    }
  } else {
    exec(successCb, errorCb, 'FileTransferBackground', 'removeUpload', [id])
  }
}

FileTransferManager.prototype.acknowledgeEvent = function (id, successCb, errorCb) {
  if (!id) {
    if (errorCb) {
      errorCb({ error: 'event id is required' })
    }
  } else {
    exec(successCb, errorCb, 'FileTransferBackground', 'acknowledgeEvent', [id])
  }
}

FileTransferManager.prototype.destroy = function (successCb, errorCb) {
  this.callback = null
  exec(successCb, errorCb, 'FileTransferBackground', 'destroy', [])
}

module.exports = {
  init: function (options, cb, ready) {
    return new FileTransferManager(options || {}, cb, ready)
  },
  FileTransferManager: FileTransferManager
}
