
### Instructions:

Install ionic (minimum v2.2.2) and cordova (minimum v6.5.0)
```bash
$ sudo npm i -g ionic cordova --unsafe-perm
```

Install the dependencies and create a build folder named www
```bash
$ npm i
$ mkdir www
```

Make sure you have install the latest android support libraries via SDK Manager.

Then, to run it on android:

```bash
$ ionic platform add android
$ ionic run android
```

For ios:
```bash
$ ionic platform add ios
$ ionic run ios
```

To run on your browser
```
$ ionic platform add browser
$ ionic run browser
```


