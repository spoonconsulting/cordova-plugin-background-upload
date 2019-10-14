var exec = require('cordova/exec')

var FileTransferManager = function (options) {
  this._handlers = {
    event: []
  }
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
    return this.options.callback({ id: payload.id, state: 'FAILED', error: 'filePath is required' })
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
    self.options.callback({ id: payload.id, state: 'FAILED', error: 'File not found: ' + payload.filePath })
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
  if (!Object.prototype.hasOwnProperty.call(this._handlers, eventName)) {
    this._handlers[eventName] = []
  }
  this._handlers[eventName].push(callback)
}

/**
 * Remove event listener.
 *
 * @param {String} eventName to match subscription.
 * @param {Function} handle function associated with event.
 */

FileTransferManager.prototype.off = function (eventName, handle) {
  if (Object.prototype.hasOwnProperty.call(this._handlers, eventName)) {
    var handleIndex = this._handlers[eventName].indexOf(handle)
    if (handleIndex >= 0) {
      this._handlers[eventName].splice(handleIndex, 1)
    }
  }
}

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
  var args = Array.prototype.slice.call(arguments)
  var eventName = args.shift()
  if (!Object.prototype.hasOwnProperty.call(this._handlers, eventName)) { return false }

  for (var i = 0, length = this._handlers[eventName].length; i < length; i++) {
    var callback = this._handlers[eventName][i]
    if (typeof callback === 'function') {
      callback.apply(undefined, args)
    } else {
      console.log('event handler: ' + eventName + ' must be a function')
    }
  }
  return true
}

module.exports = {
  init: function (options) {
    return new FileTransferManager(options || {})
  },
  FileTransferManager: FileTransferManager
}
