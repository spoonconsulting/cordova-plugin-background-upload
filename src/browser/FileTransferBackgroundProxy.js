
module.exports = {
    startUpload: function(){
        alert("browser support")
    }
};


require("cordova/exec/proxy").add("FileTransferBackground",module.exports);
