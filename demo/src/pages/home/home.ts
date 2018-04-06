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
        console.log(upload.serverResponse);
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
          var correspondingMedia = self.getMediaWithId(uploadException.id);
          if (correspondingMedia)
            correspondingMedia.updateStatus("Error while uploading");

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

  
  private cancelUpload(media: Media): void {
    this.uploader.removeUpload(media.id, res=>{
      console.log('removeUpload result: ', res);
      media.updateStatus("Aborted");
    },err=>alert('Error removing upload'));  
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
            serverUrl: "https://putsreq.com/efxwhEHBaJJeXNwkOxS8/",
            filePath: file_uris[i],
            fileKey: "file",
            id: media.id,
            headers: {
            },
            parameters: {
              
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
