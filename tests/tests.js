/* global FileTransferManager, TestUtils */

exports.defineAutoTests = function () {
  describe('Uploader', function () {
    // increase the timeout since android emulators run without acceleration on Travis and are very slow
    jasmine.DEFAULT_TIMEOUT_INTERVAL = 80000

    var sampleFile = 'tree.jpg'
    var path = ''
    var serverHost = window.cordova.platformId === 'android' ? '10.0.2.2' : 'localhost'
    var serverUrl = 'http://' + serverHost + ':3000/upload'
    var scopedExpect = window.expect

    beforeEach(function (done) {
      TestUtils.copyFileToDataDirectory(sampleFile).then(function (newPath) {
        path = newPath
        done()
      })
    })

    afterEach(function (done) {
      TestUtils.deleteFile(sampleFile).then(done)
    })

    it('exposes FileTransferManager globally', function () {
      scopedExpect(FileTransferManager).toBeDefined()
    })

    it('should have init function', function () {
      scopedExpect(FileTransferManager.init).toBeDefined()
    })

    it('should have startUpload function', function () {
      var nativeUploader = FileTransferManager.init()
      scopedExpect(nativeUploader.startUpload).toBeDefined()
    })

    it('returns an error if no argument is given', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('event', function (result) {
        scopedExpect(result).toBeDefined()
        scopedExpect(result.error).toBe('upload settings object is missing or invalid argument')
        done()
      })
      nativeUploader.startUpload(null)
    })

    it('returns an error if upload id is missing', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('event', function (result) {
        scopedExpect(result).toBeDefined()
        scopedExpect(result.error).toBe('upload id is required')
        done()
      })
      nativeUploader.startUpload({ })
    })

    it('returns an error if serverUrl is missing', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('event', function (result) {
        scopedExpect(result).toBeDefined()
        scopedExpect(result.id).toBe('123_986')
        scopedExpect(result.error).toBe('server url is required')
        done()
      })
      nativeUploader.startUpload({ id: '123_986', filePath: path })
    })

    it('returns an error if serverUrl is invalid', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('event', function (result) {
        scopedExpect(result).toBeDefined()
        scopedExpect(result.id).toBe('123_456')
        scopedExpect(result.error).toBe('invalid server url')
        done()
      })
      nativeUploader.startUpload({ id: '123_456', serverUrl: '  ' })
    })

    it('returns an error if filePath is missing', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('event', function (result) {
        scopedExpect(result).toBeDefined()
        scopedExpect(result.id).toBe('123_426')
        scopedExpect(result.error).toBe('filePath is required')
        done()
      })
      nativeUploader.startUpload({ id: '123_426', serverUrl: serverUrl })
    })

    it('sends upload progress events', function (done) {
      var nativeUploader = FileTransferManager.init()
      var cb = function (upload) {
        // scopedExpect(upload.state).toBe('UPLOADING')
        if (upload.state === 'UPLOADED') {
          nativeUploader.acknowledgeEvent(upload.eventId)
          nativeUploader.off('event', cb)
          done()
        } else if (upload.state === 'UPLOADING') {
          scopedExpect(upload.id).toBe('123_456_789')
          scopedExpect(upload.progress).toBeGreaterThan(0)
          scopedExpect(upload.eventId).toBeUndefined()
          scopedExpect(upload.error).toBeUndefined()
        }
      }
      nativeUploader.on('event', cb)
      nativeUploader.startUpload({ id: '123_456_789', serverUrl: serverUrl, filePath: path })
    })

    it('sends success callback when upload is completed', function (done) {
      var nativeUploader = FileTransferManager.init()
      var cb = function (upload) {
        if (upload.state === 'UPLOADED') {
          scopedExpect(upload.id).toBe('abc')
          scopedExpect(upload.eventId).toBeDefined()
          scopedExpect(upload.error).toBeUndefined()
          var response = JSON.parse(upload.serverResponse)
          delete response.receivedInfo.headers
          scopedExpect(response.receivedInfo).toEqual({
            originalFilename: sampleFile,
            accessMode: 'public',
            height: 4032,
            grayscale: false,
            width: 3024,
            parameters: {}
          })
          nativeUploader.acknowledgeEvent(upload.eventId)
          nativeUploader.off('event', cb)
          done()
        }
      }
      nativeUploader.on('event', cb)
      nativeUploader.startUpload({ id: 'abc', serverUrl: serverUrl, filePath: path })
    })
  })
}
