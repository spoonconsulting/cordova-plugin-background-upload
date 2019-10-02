var exec = require('cordova/exec')

var FileTransferManager = function (options) {
  this.options = options
  var that = this
  this.options.callback = function (result) {
    that.emit('event', result)
  }
  if (!this.options.parallelUploadsLimit) {
    this.options.parallelUploadsLimit = 1
  }
  exec(this.options.callback, null, 'FileTransferBackground', 'initManager', [this.options])
}

FileTransferManager.prototype.startUpload = function (payload) {
  if (!payload) {
    return this.options.callback({ state: 'FAILED', error: 'upload settings object is missing or invalid argument' })
  }

  if (!payload.id) {
    return this.options.callback({ state: 'FAILED', error: 'upload id is required' })
  }

  if (!payload.serverUrl) {
    return this.options.callback({ id: payload.id, state: 'FAILED', error: 'server url is required' })
  }

  if (payload.serverUrl.trim() === '') {
    return this.options.callback({ id: payload.id, state: 'FAILED', error: 'invalid server url' })
  }

  if (!payload.filePath) {
    if (payload.file) {
      payload.filePath = payload.file
    } else {
      return this.options.callback({ id: payload.id, state: 'FAILED', error: 'filePath is required' })
    }
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
    exec(self.options.callback, null, 'FileTransferBackground', 'startUpload', [payload])
  }, function () {
    self.options.callback({ id: payload.id, state: 'FAILED', error: 'file does not exist: ' + payload.filePath })
  })
}

FileTransferManager.prototype.removeUpload = function (id, successCb, errorCb) {
  if (!id) {
    return errorCb({ error: 'upload id is required' })
  }
  exec(successCb, errorCb, 'FileTransferBackground', 'removeUpload', [id])
}

FileTransferManager.prototype.acknowledgeEvent = function (id, successCb, errorCb) {
  if (!id && errorCb) {
    return errorCb({ error: 'event id is required' })
  }
  exec(successCb, errorCb, 'FileTransferBackground', 'acknowledgeEvent', [id])
}

FileTransferManager.prototype.addEventListener = function (callback) {
  if (typeof callback !== 'function') {
    throw new Error('event handler must be a function')
  }
  if (this.callback !== null) {
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
