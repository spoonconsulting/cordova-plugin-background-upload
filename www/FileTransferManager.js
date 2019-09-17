var exec = require('cordova/exec')

var FileTransferManager = function (options) {
  this._handlers = {
    event: []
  }
  // store the options to this object instance
  this.options = options
  var that = this
  this.options.callback = function (result) {
    that.emit('event', result)
  }
  exec(this.options.callback, null, 'FileTransferBackground', 'initManager', [options])
}

FileTransferManager.prototype.startUpload = function (payload) {
  if (payload == null) {
    return this.options.callback({ error: 'upload settings object is missing or invalid argument' })
  }

  if (!payload.id) {
    return this.options.callback({ error: 'upload id is required' })
  }

  if (payload.serverUrl == null) {
    return this.options.callback({ id: payload.id, error: 'server url is required' })
  }

  if (payload.serverUrl.trim() === '') {
    return this.options.callback({ id: payload.id, error: 'invalid server url' })
  }

  if (!payload.filePath) {
    if (payload.file) {
      payload.filePath = payload.file
    } else {
      return this.options.callback({ id: payload.id, error: 'filePath is required' })
    }
  }

  if (!payload.fileKey) {
    payload.fileKey = 'file'
  }

  if (payload.showNotification == null) {
    payload.showNotification = true
  }

  // remove the prefix for mobile urls
  payload.filePath = payload.filePath.replace('file://', '')

  exec(this.options.callback, null, 'FileTransferBackground', 'startUpload', [payload])
}

FileTransferManager.prototype.removeUpload = function (id, success, fail) {
  if (!id) {
    return fail({ error: 'upload id is required' })
  }
  exec(success, fail, 'FileTransferBackground', 'removeUpload', [id])
}

FileTransferManager.prototype.acknowledgeEvent = function (id, success, errorCb) {
  if (!id && errorCb) {
    return errorCb({ error: 'event id is required' })
  }
  exec(success, fail, 'FileTransferBackground', 'acknowledgeEvent', [id])
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
