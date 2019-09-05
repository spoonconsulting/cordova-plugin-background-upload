#import "AppDelegate+upload.h"
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
    NSString* uploadUrl = payload[@"serverUrl"];
    NSString* filePath = payload[@"filePath"];
    NSDictionary* headers = payload[@"headers"];
    NSDictionary* parameters = payload[@"parameters"];
    NSString* fileId = payload[@"id"];
    
    if (!uploadUrl)
        return [self returnError:command withInfo:@{@"id":fileId, @"message": @"invalid url"}];
    
    if (!filePath)
        return [self returnError:command withInfo:@{@"id":fileId, @"message": @"file path is required"}];
    
    if (![[NSFileManager defaultManager] fileExistsAtPath:filePath] )
        return [self returnError:command withInfo:@{@"id":fileId, @"message": @"file does not exists"}];
    
    __weak FileTransferBackground *weakSelf = self;
    [[FileUploader sharedInstance] addUpload:[NSURL URLWithString:uploadUrl]
                                    uploadId:fileId
                                     fileURL:[NSURL fileURLWithPath:filePath]
                                     headers:headers ? headers : @{}
                                  parameters:parameters ? parameters : @{}
                                     fileKey:payload[@"fileKey"]
                                onCompletion:^(NSError* error) {
                                    if (error){
                                        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                                      messageAsDictionary:@{
                                                                                                            @"error" : error.localizedDescription,
                                                                                                            @"id" : fileId
                                                                                                            }];
                                        [self.commandDelegate sendPluginResult:pluginResult callbackId:weakSelf.pluginCommand.callbackId];
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
    config.requestCachePolicy = NSURLRequestReloadIgnoringCacheData;
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
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.pluginCommand.callbackId];
}

-(void)uploadManagerDidReceieveProgress:(float)progress forUpload:(NSString*)uploadId{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{
                                                                                                                @"progress" : @(progress),
                                                                                                                @"id" : uploadId,
                                                                                                                @"state": @"UPLOADING"
                                                                                                                }];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.pluginCommand.callbackId];
}

- (void)uploadManagerDidFinishBackgroundEvents:(FileUploadManager *)manager{
    //all uploads in this session completed
    AppDelegate *appDelegate = (AppDelegate *)[[UIApplication sharedApplication] delegate];
    
    if (appDelegate.backgroundCompletionBlock) {
        void (^completionHandler)(void) = appDelegate.backgroundCompletionBlock;
        appDelegate.backgroundCompletionBlock = nil;
        completionHandler();
    }
    
}

-(void)returnError:(CDVInvokedUrlCommand *) command withInfo:(NSDictionary*)data{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsDictionary:data];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}
@end
