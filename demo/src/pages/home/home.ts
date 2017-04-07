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

  constructor(private platform: Platform, private _navCtrl: NavController, private _ngZone: NgZone) {

  }

  private openGallery(): void {

    let options = {
      maximumImagesCount: 3
    }

    ImagePicker.getPictures(options).then(
      file_uris => {
        for (var i = 0; i < file_uris.length; i++) {
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

    var options: any = {
      serverUrl: "http://requestb.in/1j0i9en1", //"http://httpbin.org/post"
      filePath: this.uri.replace("file://", ""),
      headers: {
        "clientKey": "343ssdfs34j3jwe",
        "apiKey": "testkey"
      },
      parameters: {
        "upload_preset": "my2rjjsk"
      }
    };

    var self = this;

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

  }
}
