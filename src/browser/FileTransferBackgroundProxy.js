 module.exports = {
    startUpload: function (successCb, errorCb, params) {

      var payload = params[0];
      var request = window.superagent;
      request.post(payload.serverUrl)
        .set(payload.headers != null ? payload.headers : {})
        .field(payload.parameters != null ? payload.parameters : {})
        .on('progress', function (e) {
          
          if (e.percent != null && e.percent != undefined){
            successCb({
              progress: e.percent
            },{keepCallback:true});
          }
            
        })
        .attach('file', payload.file)
        .end(function (err, res) {
          if (err != null) {
            errorCb(err);
          } else {
            console.log(res.req);
            successCb(res);
          }
        });
    }
  };

  require("cordova/exec/proxy").add("FileTransferBackground", module.exports);