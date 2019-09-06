#import <Cordova/CDV.h>
#import "FileTransferBackground.h"
#import <AFNetworking/AFNetworking.h>
#import "FileUploader.h"

@interface FileTransferBackground()
@property (nonatomic, strong) CDVInvokedUrlCommand* pluginCommand;
@end
@implementation FileTransferBackground

-(void)initManager:(CDVInvokedUrlCommand*)command{
    [FileUploader sharedInstance].delegate = self;
    self.pluginCommand = command;
    //    NSDictionary* config = command.arguments[0];
    //    parallelUploadsLimit = config[@"parallelUploadsLimit"] ? config[@"parallelUploadsLimit"] : @1;
    //TODO: handle migration of old uploads
    parallelUploadsLimit = @1;
    for (UploadEvent* event in [UploadEvent allEvents]){
        [self uploadManagerDidCompleteUpload: event];
    }
}

- (void)startUpload:(CDVInvokedUrlCommand*)command{
    NSDictionary* payload = command.arguments[0];
    //    if (![[NSFileManager defaultManager] fileExistsAtPath:filePath] )
    //        return [self returnError:command withInfo:@{@"id":fileId, @"message": @"file does not exists"}];
    
    __weak FileTransferBackground *weakSelf = self;
    [[FileUploader sharedInstance] addUpload:[NSURL URLWithString:payload[@"serverUrl"]]
                                    uploadId:payload[@"id"]
                                     fileURL:[NSURL fileURLWithPath:payload[@"filePath"]]
                                     headers:payload[@"headers"]
                                  parameters:payload[@"parameters"]
                                     fileKey:payload[@"fileKey"]
                           completionHandler:^(NSError* error) {
                               if (error){
                                   CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                                 messageAsDictionary:@{
                                                                                                       @"error" : error.localizedDescription,
                                                                                                       @"id" : payload[@"id"]
                                                                                                       }];
                                   [weakSelf.commandDelegate sendPluginResult:pluginResult callbackId:weakSelf.pluginCommand.callbackId];
                               }
                           }];
}

- (void)removeUpload:(CDVInvokedUrlCommand*)command{
    NSString* uploadId = command.arguments[0];
    [[FileUploader sharedInstance] removeUpload:uploadId];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (NSURLSessionConfiguration*)uploadManagerWillExtendSessionConfiguration:(NSURLSessionConfiguration*)config{
    config.HTTPMaximumConnectionsPerHost = parallelUploadsLimit.integerValue;
    return config;
}

- (void)uploadManagerDidCompleteUpload:(UploadEvent*)event{
    CDVPluginResult* pluginResult;
    if ([event.state isEqualToString:@"SUCCESS"]) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                     messageAsDictionary:@{
                                                           @"completed" : @YES,
                                                           @"id" : event.uploadId,
                                                           @"eventId" : event.objectID.URIRepresentation.absoluteString,
                                                           @"state" : @"UPLOADED",
                                                           @"serverResponse" : event.serverResponse,
                                                           @"statusCode" : @(event.responseStatusCode)
                                                           }];
        
    }else{
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                     messageAsDictionary:@{
                                                           @"id" : event.uploadId,
                                                           @"eventId" : event.objectID.URIRepresentation.absoluteString,
                                                           @"error" : event.error,
                                                           @"state" : @"FAILED"
                                                           }];
    }
    [pluginResult setKeepCallback:@YES];
    NSLog(@"[CD]dispatching event for %@", event.uploadId);
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.pluginCommand.callbackId];
}

-(void)uploadManagerDidReceiveProgress:(float)progress forUpload:(NSString*)uploadId{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{
                                                                                                                @"progress" : @(progress),
                                                                                                                @"id" : uploadId,
                                                                                                                @"state": @"UPLOADING"
                                                                                                                }];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.pluginCommand.callbackId];
}


-(void)returnError:(CDVInvokedUrlCommand *) command withInfo:(NSDictionary*)data{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsDictionary:data];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void)acknowledgeEvent:(CDVInvokedUrlCommand*)command{
    [[FileUploader sharedInstance] acknowledgeEventReceived:command.arguments[0]];
}
@end
