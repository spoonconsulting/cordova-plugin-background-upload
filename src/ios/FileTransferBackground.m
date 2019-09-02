/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at
 
 http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

#include <sys/types.h>
#include <sys/sysctl.h>
#include "TargetConditionals.h"
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
    //    NSDictionary* config = @1;
    parallelUploadsLimit  = @1;
    
}

- (void)startUpload:(CDVInvokedUrlCommand*)command{
    NSDictionary* payload = command.arguments[0];
    NSString* uploadUrl  = payload[@"serverUrl"];
    NSString* filePath  = payload[@"filePath"];
    NSDictionary*  headers = payload[@"headers"];
    NSDictionary* parameters = payload[@"parameters"];
    NSString* fileId = payload[@"id"];
    
    if (uploadUrl == nil) {
        return [self returnError:command withInfo:@{@"id":fileId, @"message": @"invalid url"}];
    }
    
    if (filePath == nil) {
        return [self returnError:command withInfo:@{@"id":fileId, @"message": @"file path is required"}];
    }
    
    if (![[NSFileManager defaultManager] fileExistsAtPath:filePath] ) {
        return [self returnError:command withInfo:@{@"id":fileId, @"message": @"file does not exists"}];
    }
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

- (void)uploadManager:(FileUploadManager *)manager willCreateSessionWithConfiguration:(NSURLSessionConfiguration *)configuration{
    
    configuration.HTTPMaximumConnectionsPerHost = parallelUploadsLimit.integerValue;
    configuration.requestCachePolicy = NSURLRequestReloadIgnoringCacheData;
    //configuration.discretionary = YES;
}

- (void)uploadManagerDidCompleteUpload:(UploadEvent*)event{
    CDVPluginResult* pluginResult;
    if ([event.state isEqualToString:@"SUCCESS"]) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                     messageAsDictionary:@{
                                                           @"completed" : @YES,
                                                           @"id" : event.uploadId,
                                                           @"state" : @"UPLOADED",
                                                           @"serverResponse" : event.serverResponse,
                                                           @"statusCode" : @(event.responseStatusCode)
                                                           }];
        
    }else{
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                     messageAsDictionary:@{
                                                           @"id" : event.uploadId,
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

- (void)uploadManagerDidFinishBackgroundEvents:(FileUploadManager *)manager
{
    //all uploads in this session completed
    AppDelegate *appDelegate = (AppDelegate *)[[UIApplication sharedApplication] delegate];
    
    if (appDelegate.backgroundCompletionBlock) {
        void (^completionHandler)(void) = appDelegate.backgroundCompletionBlock;
        appDelegate.backgroundCompletionBlock = nil;
        completionHandler();
    }
    
}

-(void)returnError:(CDVInvokedUrlCommand *) command withInfo:(NSDictionary*)data  {
    
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsDictionary:data];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
