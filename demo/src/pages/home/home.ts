import {
  Component,
  NgZone
} from '@angular/core';

import {
  NavController
} from 'ionic-angular';
import {
  ImagePicker
} from 'ionic-native';
import {
  Platform
} from 'ionic-angular';

/*
import {
  FileTransferManager, FileUploadOptions
} from 'cordova-plugin-background-upload';
*/
declare var FileTransferManager: any;

@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage {

  allMedia: Array < Media > = [];
  isMobile: Boolean = true;
  desktopStatus: String = "";
  uploader: any;

  constructor(private platform: Platform, private _navCtrl: NavController, private _ngZone: NgZone) {

    let self = this;
    setTimeout(() => {
      self.uploader = FileTransferManager.init();

      self.uploader.on('success', function (upload) {
        if (upload.state == 'UPLOADED') {
          console.log("upload: " + upload.id + " has been completed successfully");
          console.log(upload.serverResponse);
        } else {

          console.log("upload: " + upload.id + " has been queued successfully");
        }

      });

      self.uploader.on('progress', function (upload) {
        console.log("uploading: " + upload.id + " progress: " + upload.progress + "%");
        var correspondingMedia = self.getMediaWithPath(upload.filePath);
        if (correspondingMedia) {
          correspondingMedia.updateStatus("uploading: " + upload.progress + "%");
        }
      });

      self.uploader.on('error', function (uploadException) {
        if (uploadException.id) {
          console.log("upload: " + uploadException.id + " has failed");
          
        } else {
          console.error("uploader caught an error: " + uploadException.error);
        }

      });
    }, 1000);



  }

  private getMediaWithPath(path) {

    for (var media of this.allMedia) {
      if (media.uri.indexOf(path) != -1) {
        return media;
      }
    }

    return null;
  }

  private openGallery(): void {

    var self = this;

    ImagePicker.getPictures({
      maximumImagesCount: 3
    }).then(
      file_uris => {
        for (var i = 0; i < file_uris.length; i++) {
          this.allMedia.push(new Media(file_uris[i], this._ngZone));

          var options: any = {
            serverUrl: "https://api-de.cloudinary.com/v1_1/hclcistqq/auto/upload", //"http://httpbin.org/post"
            filePath: file_uris[i],
            headers: {
              "someKey": "testkey"
            },
            parameters: {
              "colors": 1,
              "faces": 1,
              "image_metadata": 1,
              "notification_url": "https://scpix.herokuapp.com/api/v1/cloudinary?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE0OTM3MzA5MTcsImlhdCI6MTQ5MzcxNjUxNywiYWxidW1faWQiOiJ0ZXN0X3VwbG9hZCIsIm9yZ2FuaXphdGlvbl9pZCI6IjI1MTFlYWJmLWJlZjUtNDlmNi05ZmRkLTA2YTdmMzllYjU3ZCJ9.eA5dRRqwHgehVWuZSuNaCQyyWE4fNMr1RyYtVrmIcOw",
              "phash": 1,
              "tags": "test_upload",
              "timestamp": 1494321317,
              "transformation": "a_exif",
              "type": "authenticated",
              "signature": "105286a57b32dbb2e2dc33a3c067cf69d9ba207c",
              "api_key": "549516561145346"
            }
          };

          self.uploader.startUpload(options);
        }
      },
      err => console.log('err: ' + err)
    );



  }


}


export class Media {

  uri: String;
  status: String;
  zone: NgZone;

  constructor(url: String, private _ngZone: NgZone) {
    this.uri = url.replace("file://", ""); //android path needs to be cleaned
    this.status = "";
    this.zone = _ngZone;
  }

  updateStatus(stat: String) {
    //in order to updates to propagate, we need be in angular zone
    //more info here:
    //https://www.joshmorony.com/understanding-zones-and-change-detection-in-ionic-2-angular-2/
    //example where updates are made in angular zone:
    //https://www.joshmorony.com/adding-background-geolocation-to-an-ionic-2-application/
    this.zone.run(() => {
      console.log(stat);
      this.status = stat;
    });
  }

  upload() {
    this.status = "uploading"




    /*
        new FileTransferManager().upload(options)
          .then(function (serverResponse: String) {
            //the server response can be parse to an object using JSON.stringify(server)
            //any custom error from the server like invalid signature can be handled here
            //for example, of your server returns a key 'errorMessage', you can access it as follows:
            //if (JSON.stringify(server).errorMessage != null) { //server said something went wrong }
            self.updateStatus("successfully uploaded. server response=>" + serverResponse);
          }, function (err) {
            self.updateStatus("upload error");
          }, function (progress: number) {
            self.updateStatus("uploading: " + progress + "%");
          });
          */

  }
}
