import {
  Component, NgZone
} from '@angular/core';

import {
  NavController
} from 'ionic-angular';
import {
  ImagePicker
} from 'ionic-native';

/*
import {
  FileTransferManager, FileUploadOptions
} from '../../../plugins/cordova-plugin-background-upload';
*/
declare var FileTransferManager: any;
declare var FileUploadOptions: any;
@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage {

  allMedia: Array < Media > = []

  constructor(private _navCtrl: NavController, private _ngZone: NgZone) {

  }

  private openGallery(): void {
    let options = {
      maximumImagesCount: 3
    }

    ImagePicker.getPictures(options).then(
      file_uris => {
        for (var i = 0; i < file_uris.length; i++) {
          console.log('Image URI: ' + file_uris[i]);
          this.allMedia.push(new Media(file_uris[i], this._ngZone));
        }
      },
      err => console.log('err: ' + err)
    );
  }



  upload(media: Media) {
    media.upload();
  }

}


export class Media {

  uri: String;
  status: String;
  zone: NgZone;

  constructor(url: String, private _ngZone: NgZone) {
    this.uri = url;
    this.status = "";
    this.zone = _ngZone;
  }

  upload() {
    this.status = "uploading"

    var options: any = {
      serverUrl: "http://requestb.in/14cizzj1",
      filePath: this.uri.replace("file://", ""),
      numberOfRetries: 1,
      headers: {
        "a": "asd",
        "apiKey": "testkey"
      },
      parameters: {
        "upload_preset": "my2rjjsk"
      }
    };

    var self = this;
    new FileTransferManager().upload(options)
      .then(function (serverResponse: String) {
         self.zone.run(() => {
        console.log('Success: ' + serverResponse);
        self.status = "successfully uploaded. server response=>"+serverResponse;
        });
      }, function (err) {
         self.zone.run(() => {
        console.log('Error: ' + err);
        self.status = "upload error";
        });
      }, function (progress: number) {

        self.zone.run(() => {
          console.log('upload progress: ' + progress);
           self.status = "uploading: " + progress + "%";
     
        });


      });

  }
}
