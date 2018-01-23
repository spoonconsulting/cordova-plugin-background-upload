
## Background Upload Plugin for Cordova

This plugin provides a file upload functionality that can continue to run while in background. It also includes progress updates which is suitable for long-term transfer operations for large files. Currently only 1 concurrent upload is possible at any time.

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

The plugin needs to be initialised before any upload. Ideally this should be called on application start. The uploaders will provide global events which can be used to check the progress of the uploads.
```javascript
 declare var FileTransferManager: any;

 var uploader = FileTransferManager.init();

 uploader.on('success', function(upload) {
     console.log("upload: " + upload.id + " has been completed successfully");
     console.log(upload.serverResponse);

 });

 uploader.on('progress', function(upload) {
     console.log("uploading: " + upload.id + " progress: " + upload.progress + "%");

 });

 uploader.on('error', function(uploadException) {
     if (uploadException.id) {
         console.log("upload: " + uploadException.id + " has failed");
     } else {
         console.error("uploader caught an error: " + uploadException.error);
     }
 });

```
Adding an upload is done via the ``` 
startUpload``` 
method. In case the plugin was not able to enqueue the upload, an exception will be thrown in the error event listener.
```javascript
 var payload = {
     "id": "sj5f9"
     "filePath": "/storage/emulated/0/Download/Heli.divx",
     "fileKey": "file",
     "serverUrl": "http://requestb.in/14cizzj1",
     "headers": {
         "api_key": "asdasdwere123sad"
     },
     "parameters": {
         "signature": "mysign",
         "timestamp": 112321321
     }
 };

 uploader.startUpload(payload);
```
**Configuration** 
 * id: the id of the file you want to upload (String). this will be used to track uploads
 * filePath: the absolute path for the file to upload 
 * fileKey: the name of the key to use for the file
 * serverUrl: remote server url
 * headers: custom http headers
 * parameters: custom parameters for multipart data

**To remove/abort an upload:** 
```javascript
uploader.removeUpload(uploadId, function(){
    //upload aborted
}, function(err){
    //could abort the upload
});
```

 ## iOS
The plugin runs on ios 9.0 and above. The code was based on the following blog:
 [https://krumelur.me/2015/11/25/ios-background-transfer-what-about-uploads/](https://krumelur.me/2015/11/25/ios-background-transfer-what-about-uploads/).

 Internally it uses [NSUrlSession](https://developer.apple.com/library/content/documentation/Cocoa/Conceptual/URLLoadingSystem/Articles/UsingNSURLSession.html#//apple_ref/doc/uid/TP40013509-SW44) to perform the upload in a background session. When an uploaded is initiated, it will continue until it has been completed successfully or if the user kills the application. If the application is terminated by the OS, the uploads will still continue. When the user relaunches the application, after calling the init method, the success and error events will be emitted with the ids of these uploads. If the user chose to kill the application by swiping it up from the multitasking pane, the uploads will not be continued. Upload tasks in background sessions are automatically retried by the URL loading system after network errors as decided by the OS.

## Android
The minimum api level require is 21 and the background file upload is handled by the [android-upload-service](https://github.com/gotev/android-upload-service) library. If the application is killed while uploading, either by the user or the OS, all uploads will be stopped. When the app is relaunched, the ids of these uploads will be emitted to the error listener. If an upload is added when there is no network connection, it will be retried as soon as the network becomes reachable unless the app is already killed.

## Web
If you have a web version also, you can use our javascript library designed to abstract the uploads. More info here:

[cordova background upload](https://github.com/spoonconsulting/cordova-background-upload)


## License
Cordova-plugin-background-upload is licensed under the Apache v2 License.

## Credits
Cordova-plugin-background-upload is brought to you by [Spoon Consulting](http://www.spoonconsulting.com/).
