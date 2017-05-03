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


@implementation FileTransferBackground


NSString *const FormatTypeName[5] = {
    [kFileUploadStateStopped] = @"STOPPED",
    [kFileUploadStateStarted] = @"STARTED",
    [kFileUploadStateUploaded] = @"UPLOADED",
    [kFileUploadStateFailed] = @"FAILED",
    [kFileUploadStateStopping] = @"STOPPING",
};


-(void)initManager:(CDVInvokedUrlCommand*)command{
    
    pluginCommand = command;
    NSLog(@"methodName: %@", pluginCommand.methodName);
    
    [FileUploadManager sharedInstance].delegate = self;
    [[FileUploadManager sharedInstance] start];
    
    NSArray* uploads= [[FileUploadManager sharedInstance].uploads allObjects];
    for (FileUpload *upload in uploads) {
        CDVPluginResult* pluginResult;
        if(upload.state == kFileUploadStateFailed) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                         messageAsDictionary:@{@"error":@"upload failed",
                                                               @"id" :upload.uploadUUID.UUIDString,
                                                               @"state": FormatTypeName[upload.state]
                                                               }];
            [pluginResult setKeepCallback:@YES];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            
            
        }else if(upload.state == kFileUploadStateUploaded) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                         messageAsDictionary:@{@"completed":@YES,
                                                               @"id" :upload.uploadUUID.UUIDString,
                                                               @"state": FormatTypeName[upload.state]
                                                               }];
            [pluginResult setKeepCallback:@YES];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            
        }
        
        
    }
    
}

- (void)startUpload:(CDVInvokedUrlCommand*)command
{
    
    
    NSDictionary* payload = command.arguments[0];
    NSString* uploadUrl  = payload[@"serverUrl"];
    NSString* filePath  = payload[@"filePath"];
    NSDictionary*  headers = payload[@"headers"];
    NSDictionary* parameters = payload[@"parameters"];
    
    if (uploadUrl == nil) {
        return [self returnResult:command withMsg:@"invalid url" success:false];
    }
    
    if (filePath == nil) {
        return [self returnResult:command withMsg:@"file path is required" success:false];
    }
    
    
    if (![[NSFileManager defaultManager] fileExistsAtPath:filePath] ) {
        return [self returnResult:command withMsg:@"file does not exists" success:false];
        
    }
    
    if (parameters == nil) {
        parameters = @{};
    }
    
    if (headers == nil) {
        headers = @{};
    }
    
    NSURL *                 url;
    NSMutableURLRequest *   request;
    
    //  url = [NSURL URLWithString:@"https://api.cloudinary.com/v1_1/foxfort/auto/upload"];
    //url = [NSURL URLWithString:@"https://api-de.cloudinary.com/v1_1/hclcistqq/auto/upload"];
    url = [NSURL URLWithString:@"http://requestb.in/qesje2qe"];
    
    request = [NSMutableURLRequest requestWithURL:url];
    
    [request setHTTPMethod:@"POST"];
    
    
    //    NSDictionary *formParameters = @{/*@"upload_preset" :@"my2rjjsk",*/
    //                                     @"colors": @1,
    //                                     @"faces": @1,
    //                                     @"image_metadata": @1,
    //                                     @"notification_url": @"https://scpix.herokuapp.com/api/v1/cloudinary?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE0OTM3MzA5MTcsImlhdCI6MTQ5MzcxNjUxNywiYWxidW1faWQiOiJ0ZXN0X3VwbG9hZCIsIm9yZ2FuaXphdGlvbl9pZCI6IjI1MTFlYWJmLWJlZjUtNDlmNi05ZmRkLTA2YTdmMzllYjU3ZCJ9.eA5dRRqwHgehVWuZSuNaCQyyWE4fNMr1RyYtVrmIcOw",
    //                                     @"phash": @1,
    //                                     @"tags": @"test_upload",
    //                                     @"timestamp": @1494321317,
    //                                     @"transformation": @"a_exif",
    //                                     @"type": @"authenticated",
    //                                     @"signature": @"105286a57b32dbb2e2dc33a3c067cf69d9ba207c",
    //                                     @"api_key": @"549516561145346"
    //
    //                                     };
    //    NSDictionary *_headers = @{};
    
    NSString *boundary = [NSString stringWithFormat:@"Boundary-%@", [[NSUUID UUID] UUIDString]];
    
    NSString *contentType = [NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary];
    [request setValue:contentType forHTTPHeaderField: @"Content-Type"];
    
    
    NSData *body = [self createBodyWithBoundary:boundary parameters:parameters paths:@[filePath] fieldName:@"file"];
    
    for (NSString *key in headers) {
        [request setValue:[headers objectForKey:key] forHTTPHeaderField:key];
    }
    
    
    NSString *tmpFilePath = [NSTemporaryDirectory() stringByAppendingPathComponent:boundary];
    if (![body writeToFile:tmpFilePath atomically:YES] ) {
        NSLog(@"Error writing file");
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                      messageAsDictionary:@{ @"error" : @"Error writing temp file" }];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    FileUploadManager* uploader = [FileUploadManager sharedInstance];
    
    FileUpload* job=[uploader createUploadWithRequest:request fileURL:[NSURL URLWithString:[NSString stringWithFormat:@"file:%@", tmpFilePath]]];
    if(job){
        [job start];
    }else{
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                      messageAsDictionary:@{ @"error" : @"Error adding upload" }];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    
    
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
    // In our case we don't want any (NSURLCache-level) caching to get in the way
    // of our tests, so we always disable the cache.
    
    configuration.requestCachePolicy = NSURLRequestReloadIgnoringCacheData;
    //configuration.discretionary = YES;
}

- (void)uploadManager:(FileUploadManager *)manager didChangeStateForUpload:(FileUpload *)upload{
    NSLog(@"native upload %@ progress: %f",upload.uploadUUID,upload.progress);
    
    if (upload.state == kFileUploadStateFailed){
        [self returnResult:pluginCommand withMsg:@"Upload failed" success:NO];
        return;
    }
    
    if (upload.state == kFileUploadStateUploaded) {
        //upload for a file completed
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"completed":@YES, @"id" :upload.uploadUUID.UUIDString }];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:pluginCommand.callbackId];
    }else{
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"progress" : @(upload.progress*100), @"id" :upload.uploadUUID.UUIDString }];
        [pluginResult setKeepCallback:@YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:pluginCommand.callbackId];
    }
    
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

-(void)returnResult:(CDVInvokedUrlCommand *) command withMsg: (NSString*)msg success:(bool)success {
    
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:success ? CDVCommandStatus_OK : CDVCommandStatus_ERROR messageAsString:msg];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
