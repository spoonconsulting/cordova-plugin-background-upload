
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

The plugin needs to be initialised before any upload. Ideally this should be called on application start:
```javascript
declare var FileTransferManager: any;
var uploader = FileTransferManager.init();
```
The uploader will provide global events which can be used to check the status of the uploads.
```javascript
uploader.on('event', function (event) {
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

An event has the following attributes:

Property | Comment
-------- | -------
id | id of the upload
state | state of the upload (either UPLOADED, FAILED, UPLOADING)
statusCode | response code returned by server after upload is completed
serverResponse | server response received after upload is completed
errror | error message in case of failure
errorCode | error code for any exception encountered
progress | progress for ongoing upload
platform | the platform on which the event came from (ios or android)
eventId | id of the event (to be used for acknowledgement)


Adding an upload is done via the ``` 
startUpload``` 
method. In case the plugin was not able to enqueue the upload, an error will be emitted in the global event listener.
```javascript
var payload = {
    id: "sj5f9",
    filePath: "/storage/emulated/0/Download/Heli.divx",
    fileKey: "file",
    serverUrl: "http://requestb.in/14cizzj1",
    showNotification: true,
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
**Configuration** 
 * id: the id of the file you want to upload (String). this will be used to track uploads
 * filePath: the absolute path for the file to upload 
 * fileKey: the name of the key to use for the file
 * serverUrl: remote server url
 * headers: custom http headers
 * parameters: custom parameters for multipart data
 * showNotification: show progress notification on android (true by default)
 * notificationTitle: Notification title when file is being uploaded (Android only)

**To remove/abort an upload:** 
```javascript
uploader.removeUpload(uploadId, function () {
    //upload aborted
}, function (err) {
    //could abort the upload
});
```

 ## iOS
The plugin runs on ios 10.0 and above and internally uses [AFNetworking](https://github.com/AFNetworking/AFNetworking).

 AFNetworking uses [NSURLSession](https://developer.apple.com/library/content/documentation/Cocoa/Conceptual/URLLoadingSystem/Articles/UsingNSURLSession.html#//apple_ref/doc/uid/TP40013509-SW44) under the hood to perform the upload in a background session. When an uploaded is initiated, it will continue until it has been completed successfully or if the user kills the application. If the application is terminated by the OS, the uploads will still continue. When the user relaunches the application, after calling the init method, events will be emitted with the ids of these uploads. If the user chose to kill the application by swiping it up from the multitasking pane, the uploads will not be continued. Upload tasks in background sessions are automatically retried by the URL loading system after network errors as decided by the OS.

## Android
The minimum api level require is 21 and the background file upload is handled by the [android-upload-service](https://github.com/gotev/android-upload-service) library. If the application is killed while uploading, either by the user or the OS, all uploads will be stopped. When the app is relaunched, the ids of these uploads will be emitted to the error listener. If an upload is added when there is no network connection, it will be retried as soon as the network becomes reachable unless the app is already killed.

On android Oreo, there are more strict limits on background services and it's recommended to use a foreground service with an ongoing notification to get more time for service execution: https://developer.android.com/about/versions/oreo/background
Hence to prevent the service from be killed, a progress notification is needed on Android 8+.

## Migration notes for v2.0
- When version 2 of the plugin is launched on an app containing uploads still in progress from v1 plugin version, it will mark all of them as failed so that they can be retried.
- If an upload is cancelled, an event with status `FAILED` and error code -999 will be broadcasted in the global callback on ios. It is up to the application to properly handle cancelled upload callbacks.
- v2 removes the events success, error, progress and instead uses a single event for all events delivery:
    ```
    uploader.on('event', function (event) {
        //use event.state to handle different scenarios
    });
    ```


## License
Cordova-plugin-background-upload is licensed under the Apache v2 License.

## Credits
Cordova-plugin-background-upload is brought to you by [Spoon Consulting](http://www.spoonconsulting.com/).
