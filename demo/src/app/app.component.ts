import { Component } from '@angular/core';
import { Platform } from '@ionic/angular';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent {
  constructor(private platform: Platform) {
    // iOS: Fix ts source maps
    if (this.platform.is('cordova') && this.platform.is('ios')) {
      const i = document.createElement('iframe');
      i.style.display = 'none';
      document.body.appendChild(i);
      // @ts-ignore
      window.console = i.contentWindow.console;
    }
  }

}
