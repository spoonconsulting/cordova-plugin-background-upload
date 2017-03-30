//import SwiftHTTP

@objc(FileTransferBackground) class FileTransferBackground : CDVPlugin {
    
    //static let operationQueue = OperationQueue()
    
    var operationQueue: OperationQueue {
        struct Static {
            static let instance: OperationQueue = OperationQueue()
        }
        return Static.instance
    }
    
    
    @objc(startUpload:)
    func startUpload(_ command: CDVInvokedUrlCommand) {
        
        let settingsString = command.arguments[0] as? String
        
        if settingsString == nil {
            return self.returnResult(command, "invalid payload", false)
        }
        
        //let uploadUrl = "https://api.cloudinary.com/v1_1/foxfort/image/upload"
        // let uploadUrl = "http://requestb.in/1b9i7lf1"
        //"http://httpbin.org/post"
        
        
        let path_img = Bundle.main.path(forResource: "3mb", ofType: "jpg")
        let fileUrl = URL(fileURLWithPath: path_img!)
        
        operationQueue.maxConcurrentOperationCount = 1
        
        do {
            
            let objectData = settingsString!.data(using: String.Encoding.utf8)
            let responseObject = try JSONSerialization.jsonObject(with: objectData!, options: []) as! [String:AnyObject]
            let uploadUrl  = responseObject["serverUrl"] as? String
            
            if uploadUrl == nil {
             
                return self.returnResult(command, "invalid url", false)

            }
            
            let opt = try HTTP.POST(uploadUrl!, parameters: ["upload_preset": "my2rjjsk", "file": Upload(fileUrl: fileUrl)])
            
            opt.onFinish = { response in
                if let err = response.error {
                    print("error: \(err.localizedDescription)")
                 
                    return self.returnResult(command, err.localizedDescription, false)
                    
                }
                print("opt finished: \(response.description)")
                
                self.returnResult(command, "finished")
                
            }
            
            opt.progress = { progress in
                // DispatchQueue.main.async {}
                let pluginResult = CDVPluginResult(status:  CDVCommandStatus_OK, messageAs: ["progress" : progress*100])
                pluginResult!.keepCallback = true
                self.commandDelegate!.send(
                    pluginResult,
                    callbackId: command.callbackId
                )

            }
            /*
            opt.start { response in
                // DispatchQueue.main.async {}
                 self.returnResult(command, response.text ?? "")
                
            }
            */
            
            operationQueue.addOperation(opt)
            
        } catch let error {
            print("got an error creating the request: \(error)")
            self.returnResult(command, "http request could not be created", false)
        }
   
    }
    
    func returnResult(_ command: CDVInvokedUrlCommand, _ msg: String, _ success:Bool = true){
        let pluginResult = CDVPluginResult(
            status: success ? CDVCommandStatus_OK : CDVCommandStatus_ERROR,
            messageAs: msg
        )
        pluginResult!.keepCallback = true
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }
}
