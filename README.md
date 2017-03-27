Cordova Background Upload Plugin  for Apache Cordova

==================================

API provides an advanced file upload functionality that persists beyond app termination, runs in the background and continues even when the user closed/suspended the application. The plugin includes progress updates and primarily designed for long-term transfer operations for resources like video, music, and large images.

**Sample usage**

```javascript
 var payload = {
            "filePath": "/storage/emulated/0/Download/Heli.divx",
            "serverUrl": "http://requestb.in/14cizzj1",
            "headers": {
                "api_key": "asdasdwere123sad"
            },
            "parameters": {
                "signature": "mysign",
                "timestamp": 112321321
            }
        };
 var fileTransferManager = new FileTransferManager();
 fileTransferManager.upload(payload).then(function (serverResponse) {
        console.log('Success: ' + serverResponse);
 }, function (err) {
         console.log('Error: ' + err);
  }, function (progress) {
         console.log('upload progress: ' + progress);
});

```

**Configuration** 
 * file absolute path
 * remote server url
 * custom headers
 * custom parameters for multipart data



**Supported platforms**
 * iOS (Work in progress)
 * Android
 