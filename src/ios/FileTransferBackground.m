#import "FileTransferBackground.h"

@implementation FileTransferBackground
{
    bool ignoreNextError;
}

UILocalNotification *progNotification;
- (void)pluginInitialize
{
    NSLog(@"pluginInitialize");
}

- (void)startUpload:(CDVInvokedUrlCommand*)command
{
    NSLog(@"startAsync called");

    @try
    {
        self.callbackId = command.callbackId;
        
    
        
    NSError *jsonError;
    NSData *objectData = [[command.arguments objectAtIndex:0] dataUsingEncoding:NSUTF8StringEncoding];
    self.uploadSettings = [NSJSONSerialization JSONObjectWithData:objectData
                                                         options:NSJSONReadingMutableContainers
                                                           error:&jsonError];
   
    NSLog(@"dictionary data %@",self.uploadSettings);
    NSLog(@"settings: fileName: %@", [self.uploadSettings valueForKey:@"fileName"]);
        
        NSLog(@"shownotification value: %@", [self.uploadSettings valueForKey:@"showNotification"]);
    
        NSNumber* showNotificationObj = [self.uploadSettings objectForKey:@"showNotification"];
    if ([showNotificationObj boolValue] == YES)
    {
        NSLog(@"start notification");
        
        NSString *title = [self.uploadSettings valueForKey:@"notificationText"];
        title = [title stringByReplacingOccurrencesOfString:@"{{fileName}}" withString:[self.uploadSettings valueForKey:@"fileName"]];
        title = [title stringByReplacingOccurrencesOfString:@"{{progress}}" withString:@"0"];
        
        NSString *descr = [self.uploadSettings valueForKey:@"notificationDescription"];
        descr = [descr stringByReplacingOccurrencesOfString:@"{{fileName}}" withString:[self.uploadSettings valueForKey:@"fileName"]];
        descr = [descr stringByReplacingOccurrencesOfString:@"{{progress}}" withString:@"0"];
        
    progNotification = [[UILocalNotification alloc] init];
    //localNotification.userInfo = ;
    progNotification.alertAction = @"View";
    progNotification.fireDate = [NSDate date];
    progNotification.alertTitle = title;
    progNotification.alertBody = descr;
    progNotification.applicationIconBadgeNumber = 1;
    [[UIApplication sharedApplication] scheduleLocalNotification:progNotification];
    }
    
    
        
    NSString *boundary = @"AMFormBoundary";
    NSURL *url = [NSURL URLWithString:[self.uploadSettings valueForKey:@"serverUrl"]];
    
    NSString * uniqueId = [NSString stringWithFormat:@"am.pg.NSURLSessionUploadTask:%f",[[NSDate date] timeIntervalSince1970] * 1000];
    NSURLSessionConfiguration *configuration =  [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:uniqueId];
    configuration.timeoutIntervalForRequest = 200;
    configuration.timeoutIntervalForResource = 200;
    configuration.HTTPMaximumConnectionsPerHost = 3;
    configuration.allowsCellularAccess = YES;
    configuration.networkServiceType = NSURLNetworkServiceTypeBackground;
    configuration.discretionary = NO;
        
        
        NSString *authStr = [NSString stringWithFormat:@"%@:%@", [self.uploadSettings valueForKey:@"apiUser"], [self.uploadSettings valueForKey:@"apiPass"]];
        NSData *authData = [authStr dataUsingEncoding:NSUTF8StringEncoding];
        
        NSString *base64String;
        if ([authData respondsToSelector:@selector(base64EncodedStringWithOptions:)]) {
            base64String =  [NSString stringWithFormat:@"Basic %@", [authData base64EncodedStringWithOptions:kNilOptions]];  // iOS 7+
        } else {
            base64String = [NSString stringWithFormat:@"Basic %@", [authData base64Encoding]];                              // pre iOS7
        }
        

    configuration.HTTPAdditionalHeaders = @{
                                                       @"X-API-Key"     : [self.uploadSettings valueForKey:@"apiKey"],
                                                       @"Authorization" : base64String,
                                                       @"Content-Type"  : [NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary]
                                                       };
        
    NSURLSession *session = [NSURLSession sessionWithConfiguration:configuration
                                                          delegate:self
                                                     delegateQueue:[NSOperationQueue mainQueue]];
    
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.HTTPMethod = @"POST";
    
    
    NSLog(@"request.allHTTPHeaderFields: %@", request.allHTTPHeaderFields);
    
        
        NSString *path = [NSString stringWithFormat:@"file://%@", [[NSBundle mainBundle] pathForResource:@"Mobile.Users" ofType:@"json"]];
        NSLog(@"file path to upload: %@", path);
        
        NSData   *data      = [NSData dataWithContentsOfFile:path];
        
        // Build the request body
        NSMutableData *body = [NSMutableData data];
        // Body part for the attachament. This is an image.
        NSData *imageData = UIImageJPEGRepresentation([UIImage imageNamed:@"ranking"], 0.6);
        if (imageData) {
            [body appendData:[[NSString stringWithFormat:@"--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
            [body appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"file\"; filename=\"Mobile.Users.json\"\r\n"] dataUsingEncoding:NSUTF8StringEncoding]];
            [body appendData:[@"Content-Type: application/octet-stream\r\n\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
            [body appendData:data];
            [body appendData:[[NSString stringWithFormat:@"\r\n"] dataUsingEncoding:NSUTF8StringEncoding]];
        }
        [body appendData:[[NSString stringWithFormat:@"--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];

        request.HTTPBody = body;
        
        NSLog(@"start task: %@", path);
        
    //NSString *path = [self.uploadSettings valueForKey:@"filePath"];
    //NSString *path = @"file:///private/var/mobile/Containers/Bundle/Application/ABA72822-72DA-4DF5-8C64-260E82469B57/cordovaPluginFileTransfer.app/dum13mb.pkg";
        
        /*
        NSURLSessionUploadTask *task = [session uploadTaskWithRequest:request fromData:body completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
            if (error) {
                NSLog(@"AA error = %@", error);
                return;
            }
            
            NSString *result = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
            NSLog(@"BB result = %@", result);
        }];
         */
        
       // NSURLSessionUploadTask *task = [session uploadTaskWithRequest:request fromData:body];
        
        NSURLSessionUploadTask *task = [session uploadTaskWithRequest:request fromFile:[NSURL URLWithString:path]];
        
        
        [task resume];
    }
    
    @catch ( NSException *e )
    {
        NSLog(@"startUpload error: %@", e);
        
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"error in fileupload ios"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    @finally
    {
    }
}

int64_t lastProgress = 0;
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didSendBodyData:(int64_t)bytesSent totalBytesSent:(int64_t)totalBytesSent totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend
{
    int64_t progress  = 100*totalBytesSent/totalBytesExpectedToSend;
    
    if(progress > lastProgress)
    {
        NSLog(@"didSendBodyData: %lld, totalBytesSent: %lld, totalBytesExpectedToSend: %lld, progress: %lld", bytesSent, totalBytesSent, totalBytesExpectedToSend, progress);
        
        NSMutableDictionary* progressObj = [NSMutableDictionary dictionaryWithCapacity:1];
        [progressObj setObject:[NSNumber numberWithInteger:progress] forKey:@"percentage"];
        [progressObj setObject:[NSNumber numberWithInteger:totalBytesSent] forKey:@"bytesReceived"];
        [progressObj setObject:[NSNumber numberWithInteger:totalBytesExpectedToSend] forKey:@"totalBytesToReceive"];
        NSMutableDictionary* resObj = [NSMutableDictionary dictionaryWithCapacity:1];
        [resObj setObject:progressObj forKey:@"progress"];
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:resObj];
        result.keepCallback = [NSNumber numberWithInteger: TRUE];
        [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
    }
    lastProgress = progress;
    
    if(progress >= 100)
    {
       
    }
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    if (error != nil) {
        // Something went wrong...
        NSLog(@"Background transfer is failed %@",error.description);
        return;
    }
    
    // Also check http status code
    NSHTTPURLResponse *response = (NSHTTPURLResponse *)task.response;
    if ([response statusCode] >= 300) {
        NSLog(@"Background transfer is failed, status code: %ld - %@", (long)[response statusCode], [response description]);
        return;
    }
    
    NSLog(@"Background transfer is success, status code: %ld - %@", (long)[response statusCode], [response description]);
    
    
    
    
    if (error == nil) {
        NSLog(@"Task: %@ upload complete ", task);
        NSLog(@"Download completed");
    
        NSNumber* showNotificationObj = [self.uploadSettings objectForKey:@"showNotification"];
        if ([showNotificationObj boolValue] == YES)
        {
            NSLog(@"hide progress notification");
            
            [[UIApplication sharedApplication] cancelAllLocalNotifications];
        }
        
        NSLog(@"hideNotificationWhenCompleted value: %@", [self.uploadSettings valueForKey:@"hideNotificationWhenCompleted"]);
        NSNumber* hideNotificationWhenCompletedObj = [self.uploadSettings objectForKey:@"hideNotificationWhenCompleted"];
        if ([hideNotificationWhenCompletedObj boolValue] == NO)
        {
            NSLog(@"show completed notification");
            
            
            NSString *title = [self.uploadSettings valueForKey:@"notificationTextCompleted"];
            title = [title stringByReplacingOccurrencesOfString:@"{{fileName}}" withString:[self.uploadSettings valueForKey:@"fileName"]];
            title = [title stringByReplacingOccurrencesOfString:@"{{progress}}" withString:@"100"];
            
            NSString *descr = [self.uploadSettings valueForKey:@"notificationDescriptionCompleted"];
            descr = [descr stringByReplacingOccurrencesOfString:@"{{fileName}}" withString:[self.uploadSettings valueForKey:@"fileName"]];
            descr = [descr stringByReplacingOccurrencesOfString:@"{{progress}}" withString:@"100"];
            
        UILocalNotification *localNotification = [[UILocalNotification alloc] init];
        //localNotification.userInfo = ;
        localNotification.fireDate = [NSDate date];
        localNotification.alertTitle = title;
        localNotification.alertBody = descr;
        localNotification.soundName = UILocalNotificationDefaultSoundName;
        localNotification.applicationIconBadgeNumber = 1;
        [[UIApplication sharedApplication] scheduleLocalNotification:localNotification];
        }
        
        
        NSFileManager *fileManager = [NSFileManager defaultManager];
        
        NSString *path = [NSString stringWithFormat:@"file://%@", [[NSBundle mainBundle] pathForResource:@"dum13mb" ofType:@"pkg"]];
        NSLog(@"delete file at path: %@", path);
        
        
        NSError *error;
        BOOL success = [fileManager removeItemAtPath:path error:&error];
        if (success) {
            UIAlertView *removedSuccessFullyAlert = [[UIAlertView alloc] initWithTitle:@"Congratulations:" message:@"Successfully removed" delegate:self cancelButtonTitle:@"Close" otherButtonTitles:nil];
            [removedSuccessFullyAlert show];
        }
        else
        {
            NSLog(@"Could not delete file -:%@ ",[error localizedDescription]);
        }
        
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    } else
    {
        NSLog(@"Task: %@ Upload with error: %@", task, [error localizedDescription]);
        CDVPluginResult* errorResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]];
        [self.commandDelegate sendPluginResult:errorResult callbackId:self.callbackId];
    }
}

@end

