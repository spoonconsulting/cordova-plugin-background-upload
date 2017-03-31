  module.exports = {
    startUpload: function (successCb, errorCb, params) {

      if (params[0] == null) {
        return errorCb("invalid payload");
      }

      var payload = params[0];
      if (payload.serverUrl == null) {
        return errorCb("server url is required");
      }

      if (payload.serverUrl.trim() == '') {
        return errorCb("invalid server url");
      }

      if (payload.filePath == null) {
        return errorCb("server url is required");
      }

      var request = window.superagent;
      request.post('https://api.cloudinary.com/v1_1/tyets/upload')
        .set(payload.headers != null ? payload.headers : {})
        .field(payload.parameters != null ? payload.parameters : {})
        //.field('upload_preset', 'my2rjjsk')
        .on('progress', function (e) {
          console.log('Percentage done: ', e.percent);
          if (e.percent != null && e.percent != undefined)
            successCb({
              progress: e.percent
            });
        })
        .attach('file', payload.filePath)
        .end(function (err, res) {
          console.log(res.req);
          //console.log(res);
          if (err != null) {
            errorCb(err);
          } else {
            successCb(res);
          }
        });
    }
  };



  require("cordova/exec/proxy").add("FileTransferBackground", module.exports);