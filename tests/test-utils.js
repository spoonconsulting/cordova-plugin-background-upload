/* global cordova */
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
            //file already exist
            resolve((cordova.file.dataDirectory + fileName))
          })
        }, reject)
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

  return {
    copyFileToDataDirectory: copyFileToDataDirectory,
    deleteFile: deleteFile
  }
})()
module.exports = TestUtils
