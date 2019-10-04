import { Component, NgZone } from '@angular/core';
import { ImagePicker } from 'ionic-native';
import { NavController, Platform } from 'ionic-angular';

declare var FileTransferManager: any;

@Component({
    selector: 'page-home',
    templateUrl: 'home.html'
})
export class HomePage {

    allMedia: Array < Media > = [];
    uploader: any;

    constructor(private platform: Platform, private _navCtrl: NavController, private _ngZone: NgZone) {
        this.platform.ready().then(() => {
            let self = this;
            self.uploader = FileTransferManager.init();
            self.uploader.on('event', function(event) {
                if (event.eventId)
                    self.uploader.acknowledgeEvent(event.eventId);
                if (event.state == 'UPLOADED') {
                    console.log("upload: " + event.id + " has been completed successfully");
                    console.log(event.statusCode, event.serverResponse);
                    var correspondingMedia = self.getMediaWithId(event.id);
                    correspondingMedia.updateStatus("uploaded successfully");
                } else if (event.state == 'FAILED') {
                    if (event.id) {
                        console.log("upload: " + event.id + " has failed");
                        var correspondingMedia = self.getMediaWithId(event.id);
                        correspondingMedia.updateStatus("Error while uploading");
                    } else {
                        console.error("uploader caught an error: " + event.error);
                    }
                } else if (event.state == 'UPLOADING') {
                    console.log("uploading: " + event.id + " progress: " + event.progress + "%");
                    var correspondingMedia = self.getMediaWithId(event.id);
                    correspondingMedia.updateStatus("uploading: " + event.progress + "%");
                }
            });
        })
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
        this.uploader.removeUpload(media.id, res => {
            console.log('removeUpload result: ', res);
            media.updateStatus("Aborted");
        }, err => alert('Error removing upload'));
    }

    private openGallery(): void {
      var self = this;
      ImagePicker.getPictures({
          maximumImagesCount: 3
      }).then(file_uris => {
              for (var i = 0; i < file_uris.length; i++) {
                  var media = new Media(file_uris[i], this._ngZone);
                  this.allMedia.push(media);

                  var options: any = {
                    serverUrl: "http://requestbin.net/r/tbwd8itb",
                    filePath: file_uris[i],
                    fileKey: "file",
                    id: media.id,
                    notificationTitle: "Uploading image (Job 0)",
                    showNotification: 1,
                    headers: {},
                    parameters: {
                      colors: 1,
                      faces: 1,
                      image_metadata: 1,
                      phash: 1,
                      signature: "924736486",
                      tags: "device_id_F13F74C5-4F03-B800-2F76D3C37B27",
                      timestamp: 1572858811,
                      type: "authenticated"
                    }
                  };
                  self.uploader.startUpload(options);
              }
          }, err => console.log('err: ' + err)
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