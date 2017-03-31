
@objc(FileTransferBackground) class FileTransferBackground : CDVPlugin {
    
    
    var operationQueue: OperationQueue {
        struct Static {
            static let queue: OperationQueue = OperationQueue()
        }
        return Static.queue
    }
    
    
    @objc(startUpload:)
    func startUpload(_ command: CDVInvokedUrlCommand) {
        
        let settingsString = command.arguments[0] as? String
        
        if settingsString == nil {
            return self.returnResult(command, "invalid payload", false)
        }
        
        operationQueue.maxConcurrentOperationCount = 1
        
        do {
            
            let objectData = settingsString!.data(using: String.Encoding.utf8)
            let responseObject = try JSONSerialization.jsonObject(with: objectData!, options: []) as! [String:AnyObject]
            let uploadUrl  = responseObject["serverUrl"] as? String
            let filePath  = responseObject["filePath"] as? String
            let headers = responseObject["headers"] as? [String: String]
            var parameters = responseObject["parameters"] as? [String: AnyObject]
    
            if uploadUrl == nil {
                return self.returnResult(command, "invalid url", false)
            }
            
            if filePath == nil {
                return self.returnResult(command, "file path is required ", false)
            }
            
            
            if !FileManager.default.fileExists(atPath: filePath!) {
                return self.returnResult(command, "file does not exists", false)
            }
            
            if parameters == nil {
                parameters = [:]
            }
    
            
            //check if server url exists before attempting any upload
            let checkForServerOperation = try HTTP.GET(uploadUrl!)
            checkForServerOperation.start { responseCheck in
                
                if let err = responseCheck.error {
                    print("error: \(err.localizedDescription)")
                    return self.returnResult(command, err.localizedDescription, false)
                }
                do {
                    //server is reachable, start upload
                    parameters!["file"] = Upload(fileUrl: URL(fileURLWithPath: filePath!))
                    let opt = try HTTP.POST(uploadUrl!, parameters: parameters, headers: headers)
                    
                    opt.onFinish = { response in
                        if let err = response.error {
                            print("error: \(err.localizedDescription)")
                            
                            return self.returnResult(command, err.localizedDescription, false)
                            
                        }
                        print("opt finished: \(response.description)")
                        
                        self.returnResult(command, "finished")
                        
                    }
                    
                    opt.progress = { progress in
                        
                        let pluginResult = CDVPluginResult(status:  CDVCommandStatus_OK, messageAs: ["progress" : progress*100])
                        pluginResult!.keepCallback = true
                        self.commandDelegate!.send(
                            pluginResult,
                            callbackId: command.callbackId
                        )
                        
                    }
                    /*
                     opt.start { response in
                     self.returnResult(command, response.text ?? "")
                     }
                     */
                    
                    self.operationQueue.addOperation(opt)
                    
                } catch let error {
                    print("got an error creating the request: \(error)")
                    self.returnResult(command, "http request could not be created", false)
                }
                
                
            }
            
            
            
        } catch let error {
            print("got an error creating the request: \(error)")
            self.returnResult(command, "http request could not be completed", false)
        }
        
    }
    
    func returnResult(_ command: CDVInvokedUrlCommand, _ msg: String, _ success:Bool = true){
        let pluginResult = CDVPluginResult(
            status: success ? CDVCommandStatus_OK : CDVCommandStatus_ERROR,
            messageAs: msg
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }
}
