
/* global FileTransferManager, TestUtils */

exports.defineAutoTests = function () {
  describe('Uploader', function () {
    // increase the timeout since android emulators run without acceleration on Travis and are very slow
    jasmine.DEFAULT_TIMEOUT_INTERVAL = 30000

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
    describe('Add upload', function () {
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
        nativeUploader.on('event', function (result) {
          expect(result.error).toBe('upload settings object is missing or invalid argument')
          done()
        })
        nativeUploader.startUpload(null)
      })

      it('returns an error if upload id is missing', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.on('event', function (result) {
          expect(result.error).toBe('upload id is required')
          done()
        })
        nativeUploader.startUpload({ })
      })

      it('returns an error if serverUrl is missing', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.on('event', function (result) {
          expect(result.id).toBe('test_id')
          expect(result.error).toBe('server url is required')
          done()
        })
        nativeUploader.startUpload({ id: 'test_id', filePath: path })
      })

      it('returns an error if serverUrl is invalid', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.on('event', function (result) {
          expect(result).toBeDefined()
          expect(result.id).toBe('123_456')
          expect(result.error).toBe('invalid server url')
          done()
        })
        nativeUploader.startUpload({ id: '123_456', serverUrl: '  ' })
      })

      it('returns an error if filePath is missing', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.on('event', function (result) {
          expect(result.id).toBe('some_id')
          expect(result.error).toBe('filePath is required')
          done()
        })
        nativeUploader.startUpload({ id: 'some_id', serverUrl: serverUrl })
      })

      it('sends upload progress events', function (done) {
        var nativeUploader = FileTransferManager.init()
        var cb = function (upload) {
          if (upload.state === 'UPLOADED') {
            nativeUploader.acknowledgeEvent(upload.eventId)
            nativeUploader.off('event', cb)
            done()
          } else if (upload.state === 'UPLOADING') {
            expect(upload.id).toBe('a_file_id')
            expect(typeof upload.progress).toBe('number')
            expect(upload.eventId).toBeUndefined()
            expect(upload.error).toBeUndefined()
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'a_file_id', serverUrl: serverUrl, filePath: path })
      })

      it('sends success callback when upload is completed', function (done) {
        var nativeUploader = FileTransferManager.init()
        var cb = function (upload) {
          if (upload.state === 'UPLOADED') {
            expect(upload.id).toBe('abc')
            expect(upload.eventId).toBeDefined()
            expect(upload.error).toBeUndefined()
            var response = JSON.parse(upload.serverResponse)
            delete response.receivedInfo.headers
            expect(response.receivedInfo).toEqual({
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

      it('returns server status code in event', function (done) {
        var nativeUploader = FileTransferManager.init()
        var cb = function (upload) {
          if (upload.state === 'UPLOADED') {
            expect(upload.id).toBe('pkl')
            expect(upload.statusCode).toBe(210)
            nativeUploader.acknowledgeEvent(upload.eventId)
            nativeUploader.off('event', cb)
            done()
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'pkl', serverUrl: serverUrl, filePath: path })
      })

      it('sends headers during upload', function (done) {
        var nativeUploader = FileTransferManager.init()
        var headers = { signature: 'secret_hash', source: 'test' }
        var cb = function (upload) {
          if (upload.state === 'UPLOADED') {
            expect(upload.id).toBe('plop')
            var response = JSON.parse(upload.serverResponse)
            expect(response.receivedInfo.headers.signature).toBe('secret_hash')
            expect(response.receivedInfo.headers.source).toBe('test')
            nativeUploader.acknowledgeEvent(upload.eventId)
            nativeUploader.off('event', cb)
            done()
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'plop', serverUrl: serverUrl, filePath: path, headers: headers })
      })

      it('sends parameters during upload', function (done) {
        var nativeUploader = FileTransferManager.init()
        var params = {
          role: 'tester',
          type: 'authenticated'
        }
        var cb = function (upload) {
          if (upload.state === 'UPLOADED') {
            expect(upload.id).toBe('xeon')
            var response = JSON.parse(upload.serverResponse)
            expect(response.receivedInfo.parameters).toEqual(params)
            nativeUploader.acknowledgeEvent(upload.eventId)
            nativeUploader.off('event', cb)
            done()
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'xeon', serverUrl: serverUrl, filePath: path, parameters: params })
      })

      it('can upload in parallel', function (done) {
        var nativeUploader = FileTransferManager.init({ parallelUploadsLimit: 2 })
        const ids = new Set()
        // let uploadCount = 0
        var cb = function (upload) {
          if (upload.state === 'UPLOADED') {
            nativeUploader.acknowledgeEvent(upload.eventId)
            //     uploadCount++
            //     //     if (uploadCount === 1) {
            //     //       expect(ids.has('file_1')).toBeTruthy()
            //     //       expect(ids.has('file_2')).toBeTruthy()
            //     //     } else
            //     if (uploadCount === 2) {
            nativeUploader.off('event', cb)
            done()
            //     }
          } else if (upload.state === 'UPLOADING') {
            console.log('upload progress for ', upload.id)
            ids.add(upload.id)
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'file_1', serverUrl: serverUrl, filePath: path })
        nativeUploader.startUpload({ id: 'file_2', serverUrl: serverUrl, filePath: path })
        // done()
      })

      it('sends a FAILED event if upload fails', function (done) {
        var nativeUploader = FileTransferManager.init()
        var cb = function (upload) {
          if (upload.state === 'FAILED') {
            expect(upload.id).toBe('err_id')
            expect(upload.error).toBeDefined()
            expect(upload.errorCode).toBeDefined()
            nativeUploader.acknowledgeEvent(upload.eventId)
            nativeUploader.off('event', cb)
            done()
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'err_id', serverUrl: 'dummy_url', filePath: path })
      })

      it('sends a FAILED callback if file does not exist', function (done) {
        var nativeUploader = FileTransferManager.init()
        var cb = function (upload) {
          if (upload.state === 'FAILED') {
            expect(upload.id).toBe('nox')
            expect(upload.eventId).toBeUndefined()
            expect(upload.error).toContain('file does not exist')
            nativeUploader.off('event', cb)
            done()
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'nox', serverUrl: serverUrl, filePath: '/path/fake.jpg' })
      })
    })

    describe('Remove upload', function () {
      it('should have removeUpload function', function () {
        var nativeUploader = FileTransferManager.init()
        expect(nativeUploader.removeUpload).toBeDefined()
      })

      it('returns an error if no uploadId is given', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.removeUpload(null, null, function (result) {
          expect(result.error).toBe('upload id is required')
          done()
        })
      })

      it('returns an error if undefined uploadId is given', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.removeUpload(undefined, null, function (result) {
          expect(result.error).toBe('upload id is required')
          done()
        })
      })

      it('does not return error if uploadId is given', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.removeUpload('blob', done, null)
      })

      it('sends a FAILED callback when upload is removed', function (done) {
        var nativeUploader = FileTransferManager.init()
        var cb = function (upload) {
          if (upload.state === 'FAILED') {
            expect(upload.id).toBe('xyz')
            expect(upload.eventId).toBeDefined()
            expect(upload.error).toContain('cancel')
            expect(upload.errorCode).toBe(-999)
            nativeUploader.acknowledgeEvent(upload.eventId)
            nativeUploader.off('event', cb)
            done()
          } else if (upload.state === 'UPLOADING') {
            nativeUploader.removeUpload('xyz', null, null)
          }
        }
        nativeUploader.on('event', cb)
        nativeUploader.startUpload({ id: 'xyz', serverUrl: serverUrl, filePath: path })
      })
    })

    describe('Acknowledge event', function () {
      it('should have acknowledgeEvent function', function () {
        var nativeUploader = FileTransferManager.init()
        expect(nativeUploader.acknowledgeEvent).toBeDefined()
      })

      it('returns an error if no eventId is given', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.acknowledgeEvent(null, null, function (result) {
          expect(result.error).toBe('event id is required')
          done()
        })
      })

      it('returns an error if undefined eventId is given', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.acknowledgeEvent(undefined, null, function (result) {
          expect(result.error).toBe('event id is required')
          done()
        })
      })

      it('does not return error if eventId is given', function (done) {
        var nativeUploader = FileTransferManager.init()
        nativeUploader.acknowledgeEvent('x-coredata://123/UploadEvent/p1', done, null)
      })
    })
  })
}
