/* global FileTransferManager, TestUtils */

exports.defineAutoTests = function () {
  describe('Uploader', function () {
    jasmine.DEFAULT_TIMEOUT_INTERVAL = 25000
    var sampleFile = 'tree.jpg'
    var path = ''
    var serverHost = window.cordova.platformId === 'android' ? '10.0.2.2' : 'localhost'
    var serverUrl = 'http://' + serverHost + ':3000/upload'
    var nativeUploader

    beforeEach(function (done) {
      TestUtils.copyFileToDataDirectory(sampleFile).then(function (newPath) {
        path = newPath
        done()
      })
    })

    afterEach(function (done) {
      if (nativeUploader) {
        nativeUploader.destroy()
      }
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
        nativeUploader = FileTransferManager.init({}, function (result) {})
        expect(nativeUploader.startUpload).toBeDefined()
      })

      it('returns an error if no argument is given', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {
          expect(result.error).toBe('upload settings object is missing or invalid argument')
          done()
        })
        nativeUploader.startUpload(null)
      })

      it('returns an error if upload id is missing', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {
          expect(result.error).toBe('upload id is required')
          done()
        })
        nativeUploader.startUpload({ })
      })

      it('returns an error if serverUrl is missing', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {
          expect(result.id).toBe('test_id')
          expect(result.error).toBe('server url is required')
          done()
        })
        nativeUploader.startUpload({ id: 'test_id', filePath: path })
      })

      it('returns an error if serverUrl is invalid', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {
          expect(result).toBeDefined()
          expect(result.id).toBe('123_456')
          expect(result.error).toBe('invalid server url')
          done()
        })
        nativeUploader.startUpload({ id: '123_456', serverUrl: '  ' })
      })

      it('returns an error if filePath is missing', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {
          expect(result.id).toBe('some_id')
          expect(result.error).toBe('filePath is required')
          done()
        })
        nativeUploader.startUpload({ id: 'some_id', serverUrl: serverUrl })
      })

      it('sends upload progress events', function (done) {
        nativeUploader = FileTransferManager.init({}, function (upload) {
          if (upload.state === 'UPLOADED') {
            nativeUploader.acknowledgeEvent(upload.eventId)
            done()
          } else if (upload.state === 'UPLOADING') {
            expect(upload.id).toBe('a_file_id')
            expect(typeof upload.progress).toBe('number')
            expect(upload.eventId).toBeUndefined()
            expect(upload.error).toBeUndefined()
          }
        })
        nativeUploader.startUpload({ id: 'a_file_id', serverUrl: serverUrl, filePath: path })
      })

      it('sends success callback when upload is completed', function (done) {
        nativeUploader = FileTransferManager.init({}, function (upload) {
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
            done()
          }
        })
        nativeUploader.startUpload({ id: 'abc', serverUrl: serverUrl, filePath: path })
      })

      it('returns server status code in event', function (done) {
        nativeUploader = FileTransferManager.init({}, function (upload) {
          if (upload.state === 'UPLOADED') {
            expect(upload.id).toBe('pkl')
            expect(upload.statusCode).toBe(210)
            nativeUploader.acknowledgeEvent(upload.eventId)
            done()
          }
        })
        nativeUploader.startUpload({ id: 'pkl', serverUrl: serverUrl, filePath: path })
      })

      it('sends headers during upload', function (done) {
        var headers = { signature: 'secret_hash', source: 'test' }
        nativeUploader = FileTransferManager.init({}, function (upload) {
          if (upload.state === 'UPLOADED') {
            expect(upload.id).toBe('plop')
            var response = JSON.parse(upload.serverResponse)
            expect(response.receivedInfo.headers.signature).toBe('secret_hash')
            expect(response.receivedInfo.headers.source).toBe('test')
            nativeUploader.acknowledgeEvent(upload.eventId)
            done()
          }
        })
        nativeUploader.startUpload({ id: 'plop', serverUrl: serverUrl, filePath: path, headers: headers })
      })

      it('sends parameters during upload', function (done) {
        var params = {
          role: 'tester',
          type: 'authenticated'
        }
        nativeUploader = FileTransferManager.init({}, function (upload) {
          if (upload.state === 'UPLOADED') {
            expect(upload.id).toBe('xeon')
            var response = JSON.parse(upload.serverResponse)
            expect(response.receivedInfo.parameters).toEqual(params)
            nativeUploader.acknowledgeEvent(upload.eventId)
            done()
          }
        })
        nativeUploader.startUpload({ id: 'xeon', serverUrl: serverUrl, filePath: path, parameters: params })
      })

      it('can upload in parallel', function (done) {
        var ids = new Set()
        nativeUploader = FileTransferManager.init({ parallelUploadsLimit: 2 }, function (upload) {
          if (upload.state === 'UPLOADED') {
            nativeUploader.acknowledgeEvent(upload.eventId)
            if (ids.size === 1) {
              expect(ids).toEqual(new Set(['file_1', 'file_2']))
            } else if (ids.size === 2) {
              done()
            }
          } else if (upload.state === 'UPLOADING') {
            ids.add(upload.id)
          }
        })
        nativeUploader.startUpload({ id: 'file_1', serverUrl: serverUrl, filePath: path })
        nativeUploader.startUpload({ id: 'file_2', serverUrl: serverUrl, filePath: path })
      })

      it('sends a FAILED event if upload fails', function (done) {
        nativeUploader = FileTransferManager.init({}, function (upload) {
          if (upload.state === 'FAILED') {
            expect(upload.id).toBe('err_id')
            expect(upload.error).toBeDefined()
            expect(upload.errorCode).toBeDefined()
            nativeUploader.acknowledgeEvent(upload.eventId)
            done()
          }
        })
        nativeUploader.startUpload({ id: 'err_id', serverUrl: 'dummy_url', filePath: path })
      })

      it('sends a FAILED callback if file does not exist', function (done) {
        nativeUploader = FileTransferManager.init({}, function (upload) {
          if (upload.state === 'FAILED') {
            expect(upload.id).toBe('nox')
            expect(upload.eventId).toBeUndefined()
            expect(upload.error).toContain('File not found')
            done()
          }
        })
        nativeUploader.startUpload({ id: 'nox', serverUrl: serverUrl, filePath: '/path/fake.jpg' })
      })
    })

    describe('Remove upload', function () {
      it('should have removeUpload function', function () {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        expect(nativeUploader.removeUpload).toBeDefined()
      })

      it('returns an error if no uploadId is given', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        nativeUploader.removeUpload(null, null, function (result) {
          expect(result.error).toBe('upload id is required')
          done()
        })
      })

      it('returns an error if undefined uploadId is given', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        nativeUploader.removeUpload(undefined, null, function (result) {
          expect(result.error).toBe('upload id is required')
          done()
        })
      })

      it('does not return error if uploadId is given', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        nativeUploader.removeUpload('blob', done, null)
      })

      it('sends a FAILED callback when upload is removed', function (done) {
        nativeUploader = FileTransferManager.init({}, function (upload) {
          if (upload.state === 'FAILED') {
            expect(upload.id).toBe('xyz')
            expect(upload.eventId).toBeDefined()
            expect(upload.error).toContain('cancel')
            expect(upload.errorCode).toBe(-999)
            nativeUploader.acknowledgeEvent(upload.eventId)
            done()
          } else if (upload.state === 'UPLOADING') {
            nativeUploader.removeUpload('xyz', null, null)
          }
        })
        nativeUploader.startUpload({ id: 'xyz', serverUrl: serverUrl, filePath: path })
      })
    })

    describe('Acknowledge event', function () {
      it('should have acknowledgeEvent function', function () {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        expect(nativeUploader.acknowledgeEvent).toBeDefined()
      })

      it('returns an error if no eventId is given', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        nativeUploader.acknowledgeEvent(null, null, function (result) {
          expect(result.error).toBe('event id is required')
          done()
        })
      })

      it('returns an error if undefined eventId is given', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        nativeUploader.acknowledgeEvent(undefined, null, function (result) {
          expect(result.error).toBe('event id is required')
          done()
        })
      })

      it('does not return error if eventId is given', function (done) {
        nativeUploader = FileTransferManager.init({}, function (result) {})
        nativeUploader.acknowledgeEvent('x-coredata://123/UploadEvent/p1', done, null)
      })

      it('persist event id until it is acknowledged', function (done) {
        nativeUploader = FileTransferManager.init({}, function (event1) {
          if (event1.state === 'UPLOADED') {
            nativeUploader.destroy()
            nativeUploader = FileTransferManager.init({}, function (event2) {
              expect(event2.id).toBe('unsub')
              expect(event2.eventId).toBe(event1.eventId)
              nativeUploader.acknowledgeEvent(event2.eventId)
              done()
            })
          }
        })
        nativeUploader.startUpload({ id: 'unsub', serverUrl: serverUrl, filePath: path })
      })
    })
  })
}
