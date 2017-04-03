

**Background Upload Plugin for Cordova**

This plugin provides an advanced file upload functionality that persists beyond app termination(android), runs in the background and continues even when the user closed/suspended the application. The plugin includes progress updates and primarily designed for long-term transfer operations for resources like video, music, and large images. Due to platform limitations, only 1 concurrent upload is possible at any time.

**Supported Platforms**
- iOS
- Android
- Browser


**Installation**

To add this plugin to your project using cordova cli
```
cordova plugin add https://github.com/spoonconsulting/cordova-plugin-background-uploader.git
```

To uninstall this plugin
```
cordova plugin rm cordova-plugin-background-upload
```

**Sample usage**

```javascript
 var payload = {
     "filePath": "/storage/emulated/0/Download/Heli.divx", //only on mobile
     "file": fileObject, //the file object obtained from an input type='file'. application only on browsers
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
 * file:  the file object obtained from an input type='file'. application only on browsers, ignored on ios/android
 * serverUrl: remote server url
 * headers: custom http headers
 * parameters: custom parameters for multipart data


## Browser
[SuperAgent](https://github.com/visionmedia/superagent) is used on the browser to post the requests. Since it is done via Ajax, make sure your server supports CORS ([cross origin requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS)).
Supports the following browsers:

- Latest Firefox, Chrome, Safari
- IE10 through latest

For this platform, a hook is used to inject the superagent.js into your index.html which noramlly should be present into src/index.html.
If you cannot upload via your browser, please make sure the js has been properly injected in your index.html file. If it has not, add it manually into the head tag:

```html
<head>
<script src="https://cdnjs.cloudflare.com/ajax/libs/superagent/3.5.2/superagent.js"></script>
.
.
.
</head>
```
 ## iOS
The plugin runs on ios 9.0 and above. Internally it uses the swift library [SwiftHttp](https://github.com/daltoniam/SwiftHTTP)

## Android
The minimum api level require is 21 and the background file upload is handled by the [android-upload-service](https://github.com/gotev/android-upload-service) library.

## License
Cordova-plugin-backgroun-upload is licensed under the Apache v2 License.
