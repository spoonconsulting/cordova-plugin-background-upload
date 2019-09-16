/* global FileTransferManager, TestUtils */

exports.defineAutoTests = function () {
  describe('Uploader', function () {
    // increase the timeout since android emulators run without acceleration on Travis and are very slow
    jasmine.DEFAULT_TIMEOUT_INTERVAL = 80000

    var sampleFile = 'tree.jpg'
    var path = ''
    var serverHost = window.cordova.platformId === 'android' ? '10.0.2.2' : 'localhost'
    var serverUrl = 'http://' + serverHost + ':3000/upload'

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
      expect(FileTransferManager).toBeDefined()
    })

    it('should have init function', function () {
      expect(FileTransferManager.init).toBeDefined()
    })

    it('should have startUpload function', function () {
      var nativeUploader = FileTransferManager.init()
      expect(nativeUploader.startUpload).toBeDefined()
    })

    it('returns an error if no argument is given', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('error', function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('upload settings object is missing or invalid argument')
        done()
      })
      nativeUploader.startUpload(null)
    })

    it('returns an error if serverUrl is missing', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('error', function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('server url is required')
        done()
      })
      nativeUploader.startUpload({})
    })

    it('returns an error if serverUrl is invalid', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('error', function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('invalid server url')
        done()
      })
      nativeUploader.startUpload({ serverUrl: '  ' })
    })

    it('returns an error if filePath is missing', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('error', function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('filePath is required')
        done()
      })
      nativeUploader.startUpload({ serverUrl: serverUrl })
    })

    it('returns an error if upload id is missing', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('error', function (result) {
        console.log('got error while uploading', result.error)
      })
      nativeUploader.on('error', function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('upload id is required')
        done()
      })
      nativeUploader.startUpload({ serverUrl: serverUrl, filePath: path })
    })

    it('sends upload progress events', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('error', function (result) {
        console.log('got error while uploading', result.error)
      })
      nativeUploader.on('progress', function (upload) {
        expect(upload).toBeDefined()
        expect(upload.id).toBeDefined()
        expect(upload.progress).toBeGreaterThan(0)
        done()
      })
      nativeUploader.startUpload({ serverUrl: serverUrl, filePath: path })
    })

    it('sends success callback when upload is completed', function (done) {
      var nativeUploader = FileTransferManager.init()
      nativeUploader.on('error', function (result) {
        console.log('got error while uploading', result.error)
      })
      nativeUploader.on('success', function (upload) {
        expect(upload).toBeDefined()
        expect(upload.serverResponse).toBeDefined()
        done()
      })
      nativeUploader.startUpload({ serverUrl: serverUrl, filePath: path })
    })
  })
}
