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
    if (command.arguments.count > 0){
        NSDictionary* config = command.arguments[0];
        FileUploader.parallelUploadsLimit = config[@"parallelUploadsLimit"] ? (NSInteger)config[@"parallelUploadsLimit"] : 1;
    }
    
    [FileUploader sharedInstance].delegate = self;
    //mark all old uploads as failed to be retried
    for (NSString* uploadId in [self getV1Uploads]){
        [self sendCallback:@{
            @"state" : @"FAILED",
            @"id" : uploadId,
            @"platform" : @"ios",
            @"error": @"upload failed",
            @"errorCode" : @500
        }];
    }

    for (UploadEvent* event in [UploadEvent allEvents]){
        [self uploadManagerDidReceiveCallback: [event dataRepresentation]];
    }
}


- (void)startUpload:(CDVInvokedUrlCommand*)command{
    NSDictionary* payload = command.arguments[0];
    if (![[NSFileManager defaultManager] fileExistsAtPath:payload[@"filePath"]]){
        [self sendCallback:@{
            @"id" : payload[@"id"],
            @"error" : @"file does not exists",
            @"errorCode" : @(NSFileReadNoSuchFileError),
            @"platform" : @"ios"
        }];
        return;
    }
    __weak FileTransferBackground *weakSelf = self;
    
    [[FileUploader sharedInstance] addUpload:payload
                           completionHandler:^(NSError* error) {
        if (error){
            [weakSelf sendCallback:@{
                @"error" : error.localizedDescription,
                @"id" : payload[@"id"],
                @"errorCode" : @(error.code),
                @"platform" : @"ios"
            }];
        }
    }];
}

- (void)removeUpload:(CDVInvokedUrlCommand*)command{
    [[FileUploader sharedInstance] removeUpload:command.arguments[0]];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void)uploadManagerDidReceiveCallback:(NSDictionary*)info{
    [self sendCallback:info];
}

-(void)sendCallback:(NSDictionary*)data{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:data];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.pluginCommand.callbackId];
}

-(void)acknowledgeEvent:(CDVInvokedUrlCommand*)command{
    [[FileUploader sharedInstance] acknowledgeEventReceived:command.arguments[0]];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
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
    //remove the old uploads directory
    [[NSFileManager defaultManager] removeItemAtURL:workDirectoryURL error:nil];
    return oldUploadIds;
}
@end
