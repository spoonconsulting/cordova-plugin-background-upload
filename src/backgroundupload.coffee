request = require 'superagent'
console.log request

BackgroundUpload = {
  upload: (file)->
    if window.cordova?
    else
      alert 'No cordova, use superagent'
      console.log request
}

`export default BackgroundUpload;`
