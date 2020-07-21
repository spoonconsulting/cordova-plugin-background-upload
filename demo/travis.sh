#!/bin/bash
set -o nounset
set -o errexit

cd demo

gem install bundler -v 2.1.4
bundle config set deployment 'true'
bundle install

node -v
npm install --unsafe-perm=true --allow-root -g cordova@8.1.2 ionic@5.4.1
npm install

if [[ "$TRAVIS_OS_NAME" == "linux" ]]; then
  export ANDROID_HOME=/usr/local/android-sdk
  export ANDROID_SDK_ROOT=/usr/local/android-sdk
  export PATH=/usr/local/android-sdk/build-tools/28.0.3/:${PATH}

  bundle exec fastlane android beta  
fi