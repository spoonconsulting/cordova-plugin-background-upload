/* global cordova, Image, FileReader */
var TestUtils = (function () {
  function copyFileToDataDirectory (fileName) {
    return new Promise(function (resolve, reject) {
      window.resolveLocalFileSystemURL(cordova.file.applicationDirectory + fileName, function (fileEntry) {
        window.resolveLocalFileSystemURL(cordova.file.dataDirectory, function (directory) {
          fileEntry.copyTo(directory, fileName, function () {
            resolve((cordova.file.dataDirectory + fileName))
          },
          function (err) {
            console.log(err)
            reject(err)
          })
        }, reject)
      }, reject)
    })
  }

  function getFileSize (imageUri) {
    return new Promise(function (resolve, reject) {
      window.resolveLocalFileSystemURI(imageUri,
        function (fileEntry) {
          fileEntry.file(function (fileObj) {
            resolve(fileObj.size / (1024 * 1024))
          },
          reject)
        }, reject)
    })
  }

  function deleteFile (fileName) {
    return new Promise(function (resolve, reject) {
      window.resolveLocalFileSystemURL(cordova.file.dataDirectory, function (dir) {
        dir.getFile(fileName, {
          create: false
        }, function (fileEntry) {
          fileEntry.remove(resolve, reject, reject)
        })
      }, reject)
    })
  }

  function getImageDimensions (imageURI) {
    return new Promise(function (resolve, reject) {
      window.resolveLocalFileSystemURL(imageURI, function (fileEntry) {
        fileEntry.file(function (fileObject) {
          var reader = new FileReader()
          reader.onloadend = function (evt) {
            var image = new Image()
            image.onload = function (evt) {
              image = null
              resolve({
                width: this.width,
                height: this.height
              })
            }
            image.src = evt.target.result
          }
          reader.readAsDataURL(fileObject)
        }, reject)
      })
    })
  }

  return {
    copyFileToDataDirectory: copyFileToDataDirectory,
    getFileSize: getFileSize,
    deleteFile: deleteFile,
    getImageDimensions: getImageDimensions
  }
})()
module.exports = TestUtils
