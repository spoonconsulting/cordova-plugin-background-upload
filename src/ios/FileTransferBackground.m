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

@implementation FileTransferBackground


NSString *const FormatTypeName[5] = {
    [kFileUploadStateStopped] = @"STOPPED",
    [kFileUploadStateStarted] = @"UPLOADING",
    [kFileUploadStateUploaded] = @"UPLOADED",
    [kFileUploadStateFailed] = @"FAILED",
    [kFileUploadStateStopping] = @"STOPPING",
};


-(void)initManager:(CDVInvokedUrlCommand*)command{
    [FileUploader sharedInstance].delegate = self;
    lastProgressTimeStamp = 0;
    pluginCommand = command;
    //    NSDictionary* config = @1;
    parallelUploadsLimit  = @1;
    
}

- (void)startUpload:(CDVInvokedUrlCommand*)command
{
    
    
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
    
    if (parameters == nil) {
        parameters = @{};
    }
    
    if (headers == nil) {
        headers = @{};
    }
    
    NSURL * url = [NSURL URLWithString:uploadUrl];
    
    NSMutableURLRequest * request = [NSMutableURLRequest requestWithURL:url];
    [request setHTTPMethod:@"POST"];
    
    
    NSString *boundary = [NSString stringWithFormat:@"Boundary-%@", [[NSUUID UUID] UUIDString]];
    
    NSString *contentType = [NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary];
    [request setValue:contentType forHTTPHeaderField: @"Content-Type"];
    
    
    NSData *body = [self createBodyWithBoundary:boundary parameters:parameters paths:@[filePath] fieldName:payload[@"fileKey"]];
    
    for (NSString *key in headers) {
        [request setValue:[headers objectForKey:key] forHTTPHeaderField:key];
    }
    
    
    NSString *tmpFilePath = [NSTemporaryDirectory() stringByAppendingPathComponent:boundary];
    if (![body writeToFile:tmpFilePath atomically:YES] ) {
        
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                      messageAsDictionary:@{
                                                                            @"error" : @"Error writing temp file",
                                                                            @"id" : fileId
                                                                            }];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    [[FileUploader sharedInstance] addUpload:request uploadId:fileId fileURL:[NSURL fileURLWithPath:tmpFilePath]];
    
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                  messageAsDictionary:@{
                                                                        @"error" : @"Error adding upload",
                                                                        @"id" : fileId
                                                                        }];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    
    
    
}

- (void)removeUpload:(CDVInvokedUrlCommand*)command
{
    NSString* fileId = command.arguments[0];
    FileUploadManager* uploader = [FileUploadManager sharedInstance];
    
    FileUpload* upload =[uploader getUploadById:fileId];
    if (upload){
        if (upload.state == kFileUploadStateStarted)
            [upload stop];
        [upload remove];
    }
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    
}

- (NSData *)createBodyWithBoundary:(NSString *)boundary
                        parameters:(NSDictionary *)parameters
                             paths:(NSArray *)paths
                         fieldName:(NSString *)fieldName {
    NSMutableData *httpBody = [NSMutableData data];
    
    [parameters enumerateKeysAndObjectsUsingBlock:^(NSString *parameterKey, NSString *parameterValue, BOOL *stop) {
        [httpBody appendData:[[NSString stringWithFormat:@"--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
        [httpBody appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"%@\"\r\n\r\n", parameterKey] dataUsingEncoding:NSUTF8StringEncoding]];
        [httpBody appendData:[[NSString stringWithFormat:@"%@\r\n", parameterValue] dataUsingEncoding:NSUTF8StringEncoding]];
    }];
    
    
    for (NSString *path in paths) {
        NSString *filename  = [path lastPathComponent];
        NSData   *data      = [NSData dataWithContentsOfFile:path];
        NSString *mimetype  = @"application/octet-stream";
        
        [httpBody appendData:[[NSString stringWithFormat:@"--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
        [httpBody appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"%@\"; filename=\"%@\"\r\n", fieldName, filename] dataUsingEncoding:NSUTF8StringEncoding]];
        [httpBody appendData:[[NSString stringWithFormat:@"Content-Type: %@\r\n\r\n", mimetype] dataUsingEncoding:NSUTF8StringEncoding]];
        [httpBody appendData:data];
        [httpBody appendData:[@"\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
    }
    
    [httpBody appendData:[[NSString stringWithFormat:@"--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
    
    return httpBody;
}


- (void)uploadManager:(FileUploadManager *)manager willCreateSessionWithConfiguration:(NSURLSessionConfiguration *)configuration
{
    
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
    [self.commandDelegate sendPluginResult:pluginResult callbackId:pluginCommand.callbackId];
}


-(void)uploadManagerDidReceieveProgress:(float)progress forUpload:(NSString*)uploadId{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{
                                                                                                                @"progress" : @(progress),
                                                                                                                @"id" : uploadId,
                                                                                                                @"state": @"UPLOADING"
                                                                                                                }];
    [pluginResult setKeepCallback:@YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:pluginCommand.callbackId];
}

- (void)uploadManagerDidFinishBackgroundEvents:(FileUploadManager *)manager
{
    //all uploads in this session completed
    AppDelegate *appDelegate = (AppDelegate *)[[UIApplication sharedApplication] delegate];
    
    if (appDelegate.backgroundCompletionBlock) {
        void (^completionHandler)() = appDelegate.backgroundCompletionBlock;
        appDelegate.backgroundCompletionBlock = nil;
        completionHandler();
    }
    
}

- (void)uploadManager:(FileUploadManager *)manager logWithFormat:(NSString *)format arguments:(va_list)arguments
{
    // +++ Need a better logging story; perhaps QLog from VoIPDemo.
    NSLog(@"%@", [[NSString alloc] initWithFormat:format arguments:arguments]);
}

-(void)returnError:(CDVInvokedUrlCommand *) command withInfo:(NSDictionary*)data  {
    
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsDictionary:data];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
