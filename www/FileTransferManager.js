var exec = require('cordova/exec')

var FileTransferManager = function (options) {
  this.options = options
  if (!this.options.parallelUploadsLimit) {
    this.options.parallelUploadsLimit = 1
  }
  exec(this.emit, null, 'FileTransferBackground', 'initManager', [this.options])
}

FileTransferManager.prototype.startUpload = function (payload) {
  if (!payload) {
    return this.emit({ state: 'FAILED', error: 'upload settings object is missing or invalid argument' })
  }

  if (!payload.id) {
    return this.emit({ state: 'FAILED', error: 'upload id is required' })
  }

  if (!payload.serverUrl) {
    return this.emit({ id: payload.id, state: 'FAILED', error: 'server url is required' })
  }

  if (payload.serverUrl.trim() === '') {
    return this.emit({ id: payload.id, state: 'FAILED', error: 'invalid server url' })
  }

  if (!payload.filePath) {
    return this.emit({ id: payload.id, state: 'FAILED', error: 'filePath is required' })
  }

  if (!payload.fileKey) {
    payload.fileKey = 'file'
  }

  if (payload.showNotification === null || payload.showNotification === undefined) {
    payload.showNotification = true
  }

  if (!payload.headers) {
    payload.headers = {}
  }

  if (!payload.parameters) {
    payload.parameters = {}
  }

  var self = this
  window.resolveLocalFileSystemURL(payload.filePath, function (entry) {
    payload.filePath = entry.toURL().replace('file://', '')
    exec(self.emit, null, 'FileTransferBackground', 'startUpload', [payload])
  }, function () {
    self.emit({ id: payload.id, state: 'FAILED', error: 'file does not exist: ' + payload.filePath })
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

FileTransferManager.prototype.addEventListener = function (callback) {
  if (typeof callback !== 'function') {
    throw new Error('event handler must be a function')
  }
  if (this.callback) {
    throw new Error('Callback already defined')
  }
  this.callback = callback
}

FileTransferManager.prototype.removeEventListener = function () {
  this.callback = null
}

FileTransferManager.prototype.emit = function () {
  var args = Array.prototype.slice.call(arguments)
  this.callback.apply(undefined, args)
}

module.exports = {
  init: function (options) {
    return new FileTransferManager(options || {})
  },
  FileTransferManager: FileTransferManager
}
