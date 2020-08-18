import { Component, NgZone, ViewChild, ElementRef} from '@angular/core';
import { ImagePicker } from '@ionic-native/image-picker/ngx';
import { WebView } from '@ionic-native/ionic-webview/ngx';
import { Platform } from '@ionic/angular';

import { Media } from '../model/media';

declare var FileTransferManager: any;

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
})

export class HomePage {

  allMedia: Array<Media> = [];
  pendingMedia: Array<Media> = [];
  logs: Array<String> = [];
  uploader: any;

  @ViewChild('logs_container', {read: ElementRef, static: true }) logsContainer: ElementRef;

  constructor(private platform: Platform, private _ngZone: NgZone, private imgPicker: ImagePicker, private webView: WebView) {
    this.platform.ready().then(() => {

      this.uploader = FileTransferManager.init({
        parallelUploadsLimit: 2,
        notificationTitle: 'Upload service',
        notificationContent: 'Background upload service running'
      }, event => {
        console.log('EVENT', event);
        const correspondingMedia = this.getMediaWithId(event.id);
        if (!correspondingMedia) return;

        if (event.state == 'UPLOADED') {
          this.log("Upload: " + event.id + " has been completed successfully");
          console.log(event.statusCode, event.serverResponse);
          correspondingMedia.updateStatus("Uploaded successfully");
        } else if (event.state == 'FAILED') {
          if (event.id) {
            this.log("Upload: " + event.id + " has failed");
            correspondingMedia.updateStatus("Error while uploading");
          } else {
            console.error("uploader caught an error: " + event.error);
          }
        } else if (event.state == 'UPLOADING') {
          this.log("Uploading: " + event.id + " progress: " + event.progress + "%");
          correspondingMedia.updateStatus("Uploading: " + event.progress + "%");
        }
          
        if (event.eventId)
          this.uploader.acknowledgeEvent(event.eventId);
      });
    })
  }

  cancelUpload(media: Media): void {
    this.uploader.removeUpload(media.id, res => {
      media.updateStatus("Aborted");
      console.log('removeUpload result: ', res);
      this.log("Upload: " + media.id + " aborted");
    }, err => alert('Error removing upload'));
  }

  openGallery(): void {
    this.imgPicker.getPictures({
      maximumImagesCount: 3
    }).then(file_uris => {
      if(typeof file_uris == 'string') return;
      file_uris.forEach(file_uri => {
        const media = new Media(file_uri, this.webView.convertFileSrc(file_uri), this._ngZone);
        this.allMedia.push(media);
        this.log("Upload: " + media.id + " added");
      });
      this.refreshRemainsMediaToUpload();
    }, err => console.log('err: ' + err));
  }

  startUpload(media: Media) {
    const options: any = {
      serverUrl: "https://en7paaa03bwd.x.pipedream.net/",
      filePath: media.uri,
      fileKey: "file",
      id: media.id,
      notificationTitle: "Uploading image (Job 0)",
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
    this.uploader.startUpload(options);
    media.updateStatus("Uploading...");
    this.log("Upload: " + media.id + " start");
  }

  retryUpload(media: Media) {
    media.updateStatus(null);
    this.startUpload(media);
  }

  removeUpload(media: Media) {
    this.allMedia = this.allMedia.filter(m => m.id != media.id);
    this.refreshRemainsMediaToUpload();
  }

  async uploadAll() {
    this.refreshRemainsMediaToUpload();
    while(this.pendingMedia.length > 0) {
      this.startUpload(this.pendingMedia.pop());
      await this.sleep(400);
    }
  }

  canActOnMedia(actionName: string, media:Media ):boolean {
    switch(actionName) {
      case 'startUpload':
        return !media.status;
      case 'retryUpload':
        return media.status && media.status.indexOf('Error') > -1;
      case 'cancelUpload':
        return media.status && media.status.indexOf('%') > -1;
      case 'removeUpload':
        return !media.status || (media.status && media.status.indexOf('%') < 0);  
      default:
        return true;
    }
  }

  private getMediaWithId(mediaId) {
    return this.allMedia.find(media => media.id == mediaId);
  }

  private refreshRemainsMediaToUpload() {
    this.pendingMedia = this.allMedia.filter(media => !media.status);
  }

  private log(message: String) {
    console.log(message);
    this._ngZone.run(() => {
      this.logs.push(message);
      setTimeout(() => {
        this.logsContainer.nativeElement.scrollTop = this.logsContainer.nativeElement.scrollHeight;
      },200)
    });
  }

  private async sleep(time) {
    return new Promise((resolve,reject) => setTimeout(resolve, time))
  }
}
