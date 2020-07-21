#!/bin/bash
set -o nounset
set -o errexit

nvm install 12.14.0
nvm use 12.14.0
rvm install 2.7.0
gem install bundler -v 2.1.4
bundle install --deployment
node -v
npm install --unsafe-perm=true --allow-root -g cordova@8.1.2 ionic@5.4.1
npm install

if [[ "$TRAVIS_OS_NAME" == "linux" ]]; then
  export ANDROID_HOME=/usr/local/android-sdk
  export ANDROID_SDK_ROOT=/usr/local/android-sdk
  export PATH=/usr/local/android-sdk/tools/:${PATH}
  export PATH=/usr/local/android-sdk/platform-tools/:${PATH}
  export PATH=/usr/local/android-sdk/build-tools/28.0.3/:${PATH}
  
  bundle exec fastlane android beta  
fi