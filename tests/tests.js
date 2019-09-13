/* global FileTransferManager, TestUtils */

exports.defineAutoTests = function () {
  describe('Uploader', function () {
    // increase the timeout since android emulators run without acceleration on Travis and are very slow
    jasmine.DEFAULT_TIMEOUT_INTERVAL = 80000

    var sampleFile = 'tree.jpg'
    var path = ''
    var serverUrl = window.cordova.platformId === 'android' ? 'http://10.0.2.2:3000/upload' : 'http://localhost:3000/upload'

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

    it('returns an error if no argument is given', function (done) {
      const nativeUploader = FileTransferManager.init()
      nativeUploader.startUpload(null, function () {}, function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('upload settings object is missing or invalid argument')
        done()
      })
    })

    it('returns an error if serverUrl is missing', function (done) {
      const nativeUploader = FileTransferManager.init()
      nativeUploader.startUpload({}, function () {}, function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('server url is required')
        done()
      })
    })

    it('returns an error if serverUrl is invalid', function (done) {
      console.log(path)
      const nativeUploader = FileTransferManager.init()
      nativeUploader.startUpload({ serverUrl: '  ' }, function () {}, function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('invalid server url')
        done()
      })
    })

    it('returns an error if filePath is missing', function (done) {
      const nativeUploader = FileTransferManager.init()
      nativeUploader.startUpload({ serverUrl: serverUrl }, function () {}, function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('filePath is required')
        done()
      })
    })

    it('returns an error if upload id is missing', function (done) {
      const nativeUploader = FileTransferManager.init()
      nativeUploader.startUpload({ serverUrl: serverUrl, filePath: path }, function () {}, function (result) {
        expect(result).toBeDefined()
        expect(result.error).toBe('upload id is required')
        done()
      })
    })

    it('sends upload progress events', function (done) {
      const nativeUploader = FileTransferManager.init()
      nativeUploader.on('progress', function (upload) {
        expect(upload).toBeDefined()
        expect(upload.id).toBeDefined()
        expect(upload.progress).toBeGreaterThan(0)
        done()
      })
      nativeUploader.startUpload({ serverUrl: serverUrl, filePath: path }, function () {}, function (err) {
        console.error(err)
      })
    })

    it('sends success callback when upload is completed', function (done) {
      const nativeUploader = FileTransferManager.init()
      nativeUploader.on('success', function (upload) {
        expect(upload).toBeDefined()
        expect(upload.serverResponse).toBeDefined()
        done()
      })
      nativeUploader.startUpload({ serverUrl: serverUrl, filePath: path }, function () {}, function (err) {
        console.error(err)
      })
    })
  })
}
