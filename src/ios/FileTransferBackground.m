#import <Cordova/CDV.h>
#import "FileTransferBackground.h"
#import <AFNetworking/AFNetworking.h>
#import "FileUploader.h"

@interface FileTransferBackground()
@property (nonatomic, strong) CDVInvokedUrlCommand* pluginCommand;
@end
@implementation FileTransferBackground

-(void)initManager:(CDVInvokedUrlCommand*)command{
    self.pluginCommand = command;
    //    NSDictionary* config = command.arguments[0];
    //    parallelUploadsLimit = config[@"parallelUploadsLimit"] ? config[@"parallelUploadsLimit"] : @1;
    
    [FileUploader sharedInstance].delegate = self;
    [FileUploader sharedInstance].parallelUploadsLimit = 1;
    //mark all old uploads as failed to be retried
    for (NSString* uploadId in [self getV1Uploads]){
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                      messageAsDictionary:@{
                                                                            @"state" : @"FAILED",
                                                                            @"id" : uploadId,
                                                                            @"platform" : @"ios"
                                                                            }];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.pluginCommand.callbackId];
    }
    for (UploadEvent* event in [UploadEvent allEvents]){
        [self uploadManagerDidCompleteUpload: event];
    }
}

- (void)startUpload:(CDVInvokedUrlCommand*)command{
    NSDictionary* payload = command.arguments[0];
    if (![[NSFileManager defaultManager] fileExistsAtPath:payload[@"filePath"]])
        return [self returnError:command withInfo:@{@"id" : payload[@"id"], @"message" : @"file does not exists"}];
    
    __weak FileTransferBackground *weakSelf = self;
    [[FileUploader sharedInstance] addUpload:payload
                           completionHandler:^(NSError* error) {
                               if (error){
                                   CDVPluginResult* globalPluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                                 messageAsDictionary:@{
                                                                                                       @"error" : error.localizedDescription,
                                                                                                       @"id" : payload[@"id"],
                                                                                                       @"errorCode" : @(error.code),
                                                                                                       @"platform" : @"ios"
                                                                                                       }];
                                   [weakSelf.commandDelegate sendPluginResult:globalPluginResult callbackId:weakSelf.pluginCommand.callbackId];
                               }
                           }];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)removeUpload:(CDVInvokedUrlCommand*)command{
    NSString* uploadId = command.arguments[0];
    [[FileUploader sharedInstance] removeUpload:uploadId];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
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
                                                           @"statusCode" : @(event.responseStatusCode),
                                                           @"platform" : @"ios"
                                                           }];
        
    }else{
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                     messageAsDictionary:@{
                                                           @"id" : event.uploadId,
                                                           @"eventId" : event.objectID.URIRepresentation.absoluteString,
                                                           @"error" : event.error,
                                                           @"errorCode" : @(event.errorCode),
                                                           @"state" : @"FAILED",
                                                           @"platform" : @"ios"
                                                           }];
    }
    [pluginResult setKeepCallback:@YES];
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

-(NSArray*)getV1Uploads{
    //returns uploads made by older version of the plugin
    NSMutableArray* oldUploadIds = [[NSMutableArray alloc] init];
    NSURL* cachess =  [[NSFileManager defaultManager] URLForDirectory:NSCachesDirectory inDomain:NSUserDomainMask appropriateForURL:nil create:YES error:NULL];
    NSURL* workDirectoryURL = [cachess URLByAppendingPathComponent:@"FileUploadManager"];
    NSArray* directoryContents = [[NSFileManager defaultManager] contentsOfDirectoryAtURL:workDirectoryURL
                                                               includingPropertiesForKeys:@[NSURLIsDirectoryKey]
                                                                                  options:NSDirectoryEnumerationSkipsSubdirectoryDescendants
                                                                                    error:nil];
    for (NSURL * itemURL in directoryContents) {
        NSString* directoryName = [itemURL lastPathComponent];
        if ([directoryName hasPrefix:@"Upload-"]) {
            NSString* name = [[directoryName componentsSeparatedByString:@"*"] firstObject];
            NSString* uploadId = [name stringByReplacingOccurrencesOfString:@"Upload-" withString:@""];
            [oldUploadIds addObject:uploadId];
        }
    }
    return oldUploadIds;
}
@end
