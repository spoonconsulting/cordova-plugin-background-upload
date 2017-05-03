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
  uploader: any;

  constructor(private platform: Platform, private _navCtrl: NavController, private _ngZone: NgZone) {

    let self = this;
    setTimeout(() => {
      self.uploader = FileTransferManager.init();

      self.uploader.on('success', function (upload) {
        console.log("upload: " + upload.id + " has been completed successfully");
        //console.log(upload.serverResponse);
        var correspondingMedia = self.getMediaWithId(upload.id);
        if (correspondingMedia) {
          correspondingMedia.updateStatus("uploaded successfully");
        }
      });

      self.uploader.on('progress', function (upload) {
        console.log("uploading: " + upload.id + " progress: " + upload.progress + "%");
        var correspondingMedia = self.getMediaWithId(upload.id);
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

  private getMediaWithId(mediaId) {

    for (var media of this.allMedia) {
      if (media.id == mediaId) {
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
          var media = new Media(file_uris[i], this._ngZone);
          this.allMedia.push(media);

          var options: any = {
            serverUrl: "https://api-de.cloudinary.com/v1_1/hclcistqq/auto/upload", //"http://httpbin.org/post"
            filePath: file_uris[i],
            id: media.id,
            headers: {
              "someKey": "testkey"
            },
            parameters: {
              "colors": 1,
              "faces": 1,
              "image_metadata": 1,
              "notification_url": "https://scpix.herokuapp.com/api/v1/cloudinary?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE0OTM4MjU4ODMsImlhdCI6MTQ5MzgxMTQ4MywiYWxidW1faWQiOiJ0ZXN0X3VwbG9hZCIsIm9yZ2FuaXphdGlvbl9pZCI6IjI1MTFlYWJmLWJlZjUtNDlmNi05ZmRkLTA2YTdmMzllYjU3ZCJ9.Yf2t6QIsElSAh9aU2l0p02kxMLmujQan38gppgIZaFc",
              "phash": 1,
              "tags": "test_upload",
              "timestamp": 1494416283,
              "transformation": "a_exif",
              "type": "authenticated",
              "signature": "795eb9b391098ef8fb036a07157ad44d014af8d0",
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
  id: string;

  constructor(url: String, private _ngZone: NgZone) {
    this.uri = url;
    this.status = "uploading";
    this.zone = _ngZone;
    this.id = "" + Math.random().toString(36).substr(2, 5);
  }

  updateStatus(stat: String) {
    //in order to updates to propagate, we need be in angular zone
    //more info here:
    //https://www.joshmorony.com/understanding-zones-and-change-detection-in-ionic-2-angular-2/
    //example where updates are made in angular zone:
    //https://www.joshmorony.com/adding-background-geolocation-to-an-ionic-2-application/
    this.zone.run(() => {
      this.status = stat;
    });
  }

}
