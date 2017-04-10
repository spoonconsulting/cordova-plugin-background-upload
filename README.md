
## Background Upload Plugin for Cordova

This plugin provides a file upload functionality that can continue to run while in background. It also includes progress updates which is suiltable for long-term transfer operations for large files. Due to platform limitations, only 1 concurrent upload is possible at any time.

**Supported Platforms**
- iOS
- Android


**Installation**

To install the plugin:

```
cordova plugin add cordova-plugin-background-upload --save
```

To uninstall this plugin:
```
cordova plugin rm cordova-plugin-background-upload
```

**Sample usage**

```javascript
 declare var FileTransferManager: any;
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
 fileTransferManager.upload(payload).then(function(serverResponse) {
     console.log('Success: ' + serverResponse);
 }, function(err) {
     console.log('Error: ' + err);
 }, function(progress) {
     console.log('upload progress: ' + progress);
 });

```

**Configuration** 
 * filePath: the absolute path for the file to upload (applicable only on mobile platforms)
 * serverUrl: remote server url
 * headers: custom http headers
 * parameters: custom parameters for multipart data


 ## iOS
The plugin runs on ios 9.0 and above. Internally it uses the swift library [SwiftHttp](https://github.com/daltoniam/SwiftHTTP)

## Android
The minimum api level require is 21 and the background file upload is handled by the [android-upload-service](https://github.com/gotev/android-upload-service) library.

## License
Cordova-plugin-background-upload is licensed under the Apache v2 License.
