## [4.1.1](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.1.0....4.1.1) (2024-09-23)
* **android:** Update cordova plugin file version to 8.1.0
* **iOS:** Update cordova plugin file version to 8.1.0

## [4.1.0](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.10....4.1.0) (2024-04-30)
* **android:** REVERTED Add Workers based on the number of parallelUploadsLimit and use a database to continue taking pending uploads in the Workers
* **iOS:** Removed framework tag for pods

## [4.0.10](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.9...4.0.10) (2023-03-09)
* **android:** Add Workers based on the number of parallelUploadsLimit and use a database to continue taking pending uploads in the Workers

## [4.0.9](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.8...4.0.9) (2023-02-21)
* **android:** Upgrade WorkManager(2.8.0), Room(2.5.0) and Room Compiler(2.5.0)

## [4.0.8](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.7...4.0.8) (2023-01-20)
* **android:** Add support for .db extension when assigning MediaType

## [4.0.7](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.6...4.0.7) (2022-11-15)
* **android:** Compatibility with cordova-android v11

## [4.0.6](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.5...4.0.6) (2022-03-29)
* **android:** Fix FilePath if there is a local webserver running on device
* **ios:** Fix FilePath if there is a local webserver running on device

## [4.0.5](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.4...4.0.5) (2022-03-29)
* **android:** Use multipart/form-data attribute as on iOS

## [4.0.4](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.3...4.0.4) (2022-03-29)
* **android:** Add support for devices that does not support JSON as Content-Type

## [4.0.3](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.2...4.0.3) (2022-03-28)
* **android:** Use cordova.getThreadPool() for execute Method

## [4.0.2](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.0...4.0.2) (2022-03-24)
* **android:** Used ScheduledExecutorService and OutofQuotaPolicy added for Android 12 and above

## [4.0.1](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/4.0.0...4.0.1) (2022-03-04)
* **android:** Fixed number of Threads for Executor Service and update Room

## [4.0.0](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/2.0.7...4.0.0) (2022-03-04)
* **android:** Added WorkManager to handle uploads

## [2.0.5](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/2.0.4...2.0.5) (2021-07-07)
* **android:** update demo project

## [2.0.4](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/2.0.3...2.0.4) (2021-06-18)
### Bug Fixes
* **android:** Null pointer exception on manager service destroy


## [2.0.3](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/2.0.2...2.0.3) (2021-05-03)

## [3.0.2](https://github.com/spoonconsulting/cordova-plugin-background-upload/compare/3.0.1...3.0.2) (2021-04-22)
### Bug Fixes
* **android:**  Move gotev initialisation to a separate class that so it is initialised at app startup as per the android upload wiki (gotev)


## [2.0.1](https://github.com/spoonconsulting/cordova-plugin-background-upload/releases/tag/2.0.3)
* **android:**  Http Request method null for pendingUploads. setMethod(requestMethod) was null thus raising Java NullPointerException


## [3.0.1](https://github.com/spoonconsulting/cordova-plugin-background-upload)
