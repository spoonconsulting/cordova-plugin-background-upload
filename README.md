
# cordova-plugin-background-upload
This plugin provides a file upload functionality that can continue to run even while the app is in background. It includes progress updates suitable for long-term transfer operations of large files.

[![npm version](https://badge.fury.io/js/cordova-plugin-background-upload.svg)](https://badge.fury.io/js/cordova-plugin-background-upload)
[![Build Status](https://travis-ci.org/spoonconsulting/cordova-plugin-background-upload.svg?branch=master)](https://travis-ci.org/spoonconsulting/cordova-plugin-background-upload)

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

The plugin needs to be initialised before any upload. Ideally this should be called on application start. The uploader will provide global events which can be used to check the progress of the uploads. By default, the maximum number of parallel uploads allowed is set to 1. You can override it by changing the configuration on init.
```javascript
declare var FileTransferManager: any;
var config = {};
var uploader = FileTransferManager.init(config, callback);
```

**Methods**

### uploader.init(config, callback)
Initialises the uploader with provided configuration. To control the number of parallel uploads, pass `parallelUploadsLimit` in config.
The callback is used to track progress of the uploads
`var uploader = FileTransferManager.init({parallelUploadsLimit: 2}, event => {});`

### uploader.startUpload(payload)
Adds an upload. In case the plugin was not able to enqueue the upload, an error will be emitted in the global event listener.
```javascript
var payload = {
    id: "c3a4b4c7-4f1e-4c69-a951-773602e269fb",
    filePath: "/storage/emulated/0/Download/Heli.divx",
    fileKey: "file",
    serverUrl: "http://requestb.in/14cizzj1",
    notificationTitle: "Uploading images",
    headers: {
        api_key: "asdasdwere123sad"
    },
    parameters: {
        signature: "a_signature_hash",
        timestamp: 112321321
    }
};
uploader.startUpload(payload);
```
Param | Description
-------- | -------
id | a unique id of the file (UUID string)
filePath | the absolute path for the file to upload
fileKey | the name of the key to use for the file
serverUrl | remote server url
headers | custom http headers
parameters | custom parameters for multipart data
notificationTitle | Notification title when file is being uploaded (Android only)


### uploader.removeUpload(uploadId, successCallback, errorCallback)
Cancels and removes an upload
```javascript
uploader.removeUpload(uploadId, function () {
    //upload aborted
}, function (err) {
    //could not abort the upload
});
```


### uploader.acknowledgeEvent(eventId)
Confirms event received and remove it from plugin cache
```javascript
uploader.acknowledgeEvent(eventId);
```


The uploader will provide global events which can be used to check the status of the uploads.
```javascript
FileTransferManager.init({}, function (event) {
    if (event.state == 'UPLOADED') {
        console.log("upload: " + event.id + " has been completed successfully");
        console.log(event.statusCode, event.serverResponse);
    } else if (event.state == 'FAILED') {
        if (event.id) {
            console.log("upload: " + event.id + " has failed");
        } else {
            console.error("uploader caught an error: " + event.error);
        }
    } else if (event.state == 'UPLOADING') {
        console.log("uploading: " + event.id + " progress: " + event.progress + "%");
    }
});

```

To prevent any event loss while transitioning between native and Javascript side, the plugin stores success/failure events on disk. Once you have received the event, you will need to acknowledge it else it will be broadcast again when the plugin is initialised. Progress events do not have eventId and are not persisted.
```javascript
if (event.eventId) {
    uploader.acknowledgeEvent(event.eventId, function(){
        //success
    }, function (error){
        //error
    });
}
```
An event has the following attributes:

Property | Comment
-------- | -------
id | id of the upload
state | state of the upload (either `UPLOADING`, `UPLOADED` or `FAILED`)
statusCode | response code returned by server after upload is completed
serverResponse | server response received after upload is completed
error | error message in case of failure
errorCode | error code for any exception encountered
progress | progress for ongoing upload
eventId | id of the event


 ## iOS
The plugin runs on ios 10.0 and above and internally uses [AFNetworking](https://github.com/AFNetworking/AFNetworking). AFNetworking uses [NSURLSession](https://developer.apple.com/library/content/documentation/Cocoa/Conceptual/URLLoadingSystem/Articles/UsingNSURLSession.html#//apple_ref/doc/uid/TP40013509-SW44) under the hood to perform the upload in a background session. When an upload is initiated, it will continue until it has been completed successfully or until the user kills the application. If the application is terminated by the OS, the uploads will still continue. When the user relaunches the application, after calling the init method, events will be emitted with the ids of these uploads. If the user kills the application by swiping it up from the multitasking pane, the uploads will not be continued. Upload tasks in background sessions are automatically retried by the URL loading system after network errors as decided by the OS.

## Android
The minimum API level required is 21 and the background file upload is handled by the [android-upload-service](https://github.com/gotev/android-upload-service) library. If you have configured a notification to appear in the notifications area, the uploads will continue even if the user kills the app manually. If an upload is added when there is no network connection, it will be retried as soon as the network becomes reachable unless the app has already been killed.

On Android Oreo and above, there are strict limitations on background services and it's recommended to use a foreground service with an ongoing notification to get more OS time for service execution: https://developer.android.com/about/versions/oreo/background. Hence to prevent the service from being killed, a progress notification is needed on Android 8+.

## Migration notes for v2.0
- When v2 of the plugin is launched on an app containing uploads still in progress from v1 version, it will mark all of them as `FAILED` with `errorCode` 500 so that they can be retried.
- If an upload is cancelled, an event with status `FAILED` and error code `-999` will be broadcasted in the global callback. It is up to the application to properly handle cancelled callbacks.
- v2 removes the events `success`, `error`, `progress` and instead uses a single callback for all events delivery:
    ```javascript
    uploader.on('event', function (event) {
        //use event.state to handle different scenarios
    });
    ```
- Events need to be acknowledged to be removed. Failure to do so will result in all saved events being broadcast on `init`.
-`showNotification` parameter has been removed (A notification will always be shown on Android during upload)

## README for v1.0
The README for the previous version can be found [here](https://github.com/spoonconsulting/cordova-plugin-background-upload/blob/eacce4385ae497188307a9944c2f353571a463a2/README.md).

## License
Cordova-plugin-background-upload is licensed under the Apache v2 License.

## Credits
Cordova-plugin-background-upload is brought to you by [Spoon Consulting Ltd](http://www.spoonconsulting.com/).
