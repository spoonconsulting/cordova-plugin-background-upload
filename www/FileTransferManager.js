var exec = require('cordova/exec')

var FileTransferManager = function (options, callback) {
  this.options = options

  if (!this.options.parallelUploadsLimit) {
    this.options.parallelUploadsLimit = 1
  }

  if (typeof callback !== 'function') {
    throw new Error('event handler must be a function')
  }

  this.callback = callback
  exec(this.callback, null, 'FileTransferBackground', 'initManager', [this.options])
}

FileTransferManager.prototype.startUpload = function (payload, successCb, errorCb) {
  if (!payload) {
    return errorCb ? errorCb({ state: 'FAILED', error: 'Upload Settings object is missing or has invalid arguments' }) : null
  }

  if (!payload.id) {
    return errorCb ? errorCb({ state: 'FAILED', error: 'Upload ID is required' }) : null
  }

  if (!payload.serverUrl) {
    return errorCb ? errorCb({ id: payload.id, state: 'FAILED', error: 'Server URL is required' }) : null
  }

  if (payload.serverUrl.trim() === '') {
    return errorCb ? errorCb({ id: payload.id, state: 'FAILED', error: 'Invalid server URL' }) : null
  }

  if (!payload.filePath) {
    return errorCb ? errorCb({ id: payload.id, state: 'FAILED', error: 'filePath is required' }) : null
  }

  if (!payload.fileKey) {
    payload.fileKey = 'file'
  }

  payload.notificationTitle = payload.notificationTitle || 'Uploading files'

  if (!payload.headers) {
    payload.headers = {}
  }

  if (!payload.parameters) {
    payload.parameters = {}
  }

  if (!payload.requestMethod) {
    payload.requestMethod = 'POST'
  }

  window.resolveLocalFileSystemURL(payload.filePath, function (entry) {
    payload.filePath = entry.toURL().replace('file://', '')
    exec(successCb, errorCb, 'FileTransferBackground', 'startUpload', [payload])
  }, function () {
    if (typeof errorCb === 'function') { errorCb({ id: payload.id, state: 'FAILED', error: 'File not found: ' + payload.filePath }) }
  })
}

FileTransferManager.prototype.removeUpload = function (id, successCb, errorCb) {
  if (!id) {
    if (errorCb) {
      errorCb({ error: 'Upload ID is required' })
    }
  } else {
    exec(successCb, errorCb, 'FileTransferBackground', 'removeUpload', [id])
  }
}

FileTransferManager.prototype.acknowledgeEvent = function (id, successCb, errorCb) {
  if (!id) {
    if (errorCb) {
      errorCb({ error: 'Event ID is required' })
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
  init: function (options, cb) {
    return new FileTransferManager(options || {}, cb)
  },
  FileTransferManager: FileTransferManager
}
