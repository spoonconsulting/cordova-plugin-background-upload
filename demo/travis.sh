#!/bin/bash
set -o nounset
set -o errexit

gem install bundler -v 2.1.4
bundle install --deployment
node -v
npm install --unsafe-perm=true --allow-root -g cordova@8.1.2 ionic@5.4.1
npm install

if [[ "$TRAVIS_OS_NAME" == "linux" ]]; then
  export ANDROID_HOME=/usr/local/android-sdk
  export ANDROID_SDK_ROOT=/usr/local/android-sdk

  bundle exec fastlane android beta  
fi