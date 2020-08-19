import { Component, OnInit } from '@angular/core';
import { NavController } from '@ionic/angular';
import { NativeStorage } from '@ionic-native/native-storage/ngx';
import { Options } from '../model/options';
import { EventsService } from '../services/events.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.page.html',
  styleUrls: ['./settings.page.scss'],
})
export class SettingsPage implements OnInit {

  selectedOption: 'default' | 'custom' = 'default';
  customOptions : Options = {
    serverUrl: 'https://en7paaa03bwd.x.pipedream.net',
    fileKey: 'file',
    requestMethod: 'POST',
    headers: [],
    parameters: {},
  }

  constructor(private events:EventsService, private nativeStorage: NativeStorage, private navController: NavController) { }

  async ngOnInit() {
    try {
      const customOptions = await this.nativeStorage.getItem('upload_custom_options');
      const useCustomOption = await this.nativeStorage.getItem('upload_use_custom_options');

      if(customOptions) this.customOptions = customOptions;
      this.selectedOption = useCustomOption ? 'custom' : 'default';

    }catch(error) {}

  }

  addCustomHeader() {
    const customHeaders = this.customOptions.headers;
    const lastCustomHeaderId = customHeaders.length > 0 ? customHeaders[customHeaders.length -1].id : - 1;

    this.customOptions.headers.push({
      id: lastCustomHeaderId + 1,
      key: '',
      value: ''
    });
  }

  removeCustomHeader(id) {
    this.customOptions.headers = this.customOptions.headers.filter(header => header.id != id);
  }

  save() {
    this.nativeStorage.setItem('upload_custom_options', this.customOptions);
    this.nativeStorage.setItem('upload_use_custom_options', this.selectedOption == 'default' ? false : true);
    this.events.publishUploadOptionsChange(true);
    this.navController.back();
  }

}
