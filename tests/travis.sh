#!/bin/bash
set -o nounset
set -o errexit

npm install -g cordova npx
npm install

# lint
npm run lint
mkdir ~/test_results
# run tests appropriate for platform
if [[ "$TRAVIS_OS_NAME" == "osx" ]]; then
    gem install cocoapods
    pod repo update
    npm install -g ios-sim ios-deploy
    npm run test:ios
    # cat ~/test_results/ios_logs.txt
fi
if [[ "$TRAVIS_OS_NAME" == "linux" ]]; then
    echo no | android create avd --force -n test -t android-22 --abi armeabi-v7a
    emulator -avd test -no-audio -no-window &
    android-wait-for-emulator
    npm run test:android
    # cat ~/test_results/android_logs.txt
fi