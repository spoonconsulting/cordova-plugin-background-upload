import { Component, NgZone } from '@angular/core';
import { AlertController, Platform } from '@ionic/angular';
import { ImagePicker } from '@awesome-cordova-plugins/image-picker/ngx';
import { File } from '@awesome-cordova-plugins/file/ngx';
import { FileTransferManager, BackgroundUpload } from '@awesome-cordova-plugins/background-upload/ngx';

const TEST_UPLOAD_URL = 'https://api.2ip.me/api-speedtest/en/dev.null';
const ID_OFFSET = 100;

@Component({
  selector: 'app-tab1',
  templateUrl: 'tab1.page.html',
  styleUrls: ['tab1.page.scss'],
  providers: [ImagePicker, File, BackgroundUpload],
})
export class Tab1Page {
  uploader: FileTransferManager;

  images: Map<number, string> = new Map();
  imageUris: Map<number, string> = new Map();
  uploadStates: Map<number, UploadState> = new Map();

  private isCordova = this.platform.is('cordova');

  constructor(
    private platform: Platform,
    private zone: NgZone,
    private alertController: AlertController,
    private imagePicker: ImagePicker,
    private file: File,
    private backgroundUpload: BackgroundUpload
  ) {
    this.platform.ready().then(() => {

      this.initUpload();

    });
  }

  async onPickImage() {
    if (!this.isCordova) {
      return
    }
    // Check permissions beforehand because if we let imagePicker do it
    // he will return nonsense
    const hasPermissions = await this.imagePicker.hasReadPermission();
    if (!hasPermissions) {
      await this.imagePicker.requestReadPermission();
      return;
    }

    const options = {
      maximumImagesCount: 100
    };

    try {
      const uris: Array<string> = await this.imagePicker.getPictures(options);
      const generatedKeys = this.generateUniqueIds(uris.length);
      console.log(uris);
      uris.forEach((uri, i) => {
        const pathSplit = uri.split('/');
        const dir = 'file://' + pathSplit.join('/');
        this.imageUris.set(generatedKeys[i], dir);
      });

      const data = await Promise.all(uris.map((uri) => {
        const pathSplit = uri.split('/');
        const filename = pathSplit.pop();
        const dir = 'file://' + pathSplit.join('/');
        return this.file.readAsDataURL(dir, filename);
      }));

      data.forEach((d, i) => {
        this.images.set(generatedKeys[i], d);
      });
    } catch (err) {
      const alert = await this.alertController.create({
        header: 'An error occurred',
        message: JSON.stringify(err),
        buttons: ['Ok'],
      });

      await alert.present();
    }
  }

  async onClickImage(id: number) {
    if (!this.uploadStates.has(id)) {
      // Start upload
      this.uploadImage(id);
    } else {
      // Remove download
      await this.removeImage(id);
    }
  }

  async onTapUploadButton() {
    for (const [key, value] of this.images) {
      if (!this.uploadStates.has(key)) {
        // Start upload
        this.uploadImage(key);
      } else {
        // Remove download
        await this.removeImage(key);
      }
    }
  }

  private initUpload(){
    if(!this.isCordova){
      return;
    }
    if(this.uploader){
      return;
    }
    this.uploader = this.backgroundUpload.init({
      callBack: event => {
        console.log(event);
        this.zone.run(() => {
          const id = Number.parseInt(event.id, 10);

          if (!this.uploadStates.has(id)) {
            this.uploadStates.set(id, new UploadState());
          }
          const state = this.uploadStates.get(id);

          switch (event.state) {
            case 'UPLOADING':
              state.status = UploadStatus.InProgress;
              state.progress = event.progress / 100.0;
              break;

            case 'UPLOADED':
              state.status = UploadStatus.Done;
              state.progress = 1.0;
              break;

            case 'FAILED':
              state.status = UploadStatus.Failed;
              this.alertController.create({
                header: 'Upload failed',
                message: event.error,
                buttons: ['Ok'],
              })
                .then((alert) => alert.present());
              break;
          }

          console.log('New state:', state);

          if (event.eventId) {
            console.log('ACK');
            this.uploader.acknowledgeEvent(event.eventId);
          }
        });
      }
    })
  }

  private uploadImage(id: number) {
    for (let i = 0; i < 10; i++) {
      const uri = this.imageUris.get(id);
      console.log('Upload id', id);

      const options = {
        serverUrl: TEST_UPLOAD_URL,
        filePath: uri,
        fileKey: 'file',
        id: String(id),
        notificationTitle: 'Uploading image'
      };
      this.uploader.startUpload(options);
      console.log('Upload submitted');
    }
  }

  private async removeImage(id: number) {
    const state = this.uploadStates.get(id);
    const res = await this.uploader.removeUpload(id);
    if (res) {
      console.log('Remove result:', res);
      this.zone.run(() => {
        state.status = UploadStatus.Aborted;
        state.progress = 1.0;
      });
    } else {
      console.warn('Remove error:', res);
      const alert = await this.alertController.create({
        header: 'Error removing upload',
      });
      await alert.present();
    }
  }

  private generateUniqueIds(count: number): Array<number> {
    const random = () => Math.round(Math.random() * 10000);
    const keys = Array(count).fill(undefined);

    for (let i = 0; i < count; i++) {
      let key = random();
      while (this.imageUris.has(key) || keys.includes(key) || key === 0) {
        key = random();
      }
      keys[i] = key;
    }

    return keys;
  }
}

export enum UploadStatus {
  // eslint-disable-next-line @typescript-eslint/naming-convention
  InProgress,
  // eslint-disable-next-line @typescript-eslint/naming-convention
  Done,
  // eslint-disable-next-line @typescript-eslint/naming-convention
  Failed,
  // eslint-disable-next-line @typescript-eslint/naming-convention
  Aborted,
}

export class UploadState {
  status = UploadStatus.InProgress;
  progress = 0.0;

  get color(): string {
    switch (this.status) {
      case UploadStatus.InProgress:
        return 'tertiary';
      case UploadStatus.Done:
        return 'success';
      case UploadStatus.Failed:
        return 'danger';
      case UploadStatus.Aborted:
        return 'dark';
    }
  }
}
