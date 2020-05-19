import { NgZone } from '@angular/core';

export class Media {

  uri: String;
  local_uri: String;
  status: String;
  zone: NgZone;
  id: string;

  constructor(uri: String, local_uri: String, private _ngZone: NgZone) {
    this.uri = uri;
    this.local_uri = local_uri;
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