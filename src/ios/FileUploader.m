//
//  FileUploader.m
//  SharinPix
//
//  Created by Mevin Dhunnooa on 29/08/2019.
//

#import "FileUploader.h"
@interface FileUploader()
@property (nonatomic, strong) NSMutableDictionary* responsesData;
@end
@implementation FileUploader
+ (instancetype)sharedInstance
{
    static FileUploader *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[FileUploader alloc] init];
    });
    return sharedInstance;
}
- (id)init{
    self = [super init];
    if (self == nil)
        return nil;
    self.responsesData = [[NSMutableDictionary alloc] init];
    configuration = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:@"com.spoon.BackgroundUpload.session"];
    configuration.HTTPMaximumConnectionsPerHost = 1;
    
    manager = [[AFURLSessionManager alloc] initWithSessionConfiguration:configuration];
    __weak FileUploader *weakSelf = self;
    [manager setTaskDidCompleteBlock:^(NSURLSession * _Nonnull session, NSURLSessionTask * _Nonnull task, NSError * _Nullable error) {
        UploadEvent* event = [[UploadEvent alloc] init];
        event.uploadId = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
        if (!error){
            event.state = @"SUCCESS";
            event.responseStatusCode = ((NSHTTPURLResponse *)task.response).statusCode;
            event.serverResponse = weakSelf.responsesData[@(task.taskIdentifier)];
            [weakSelf.responsesData removeObjectForKey:@(task.taskIdentifier)];
        } else {
            event.state = @"FAILED";
            // The upload was stopped by the network.
            event.error = error.localizedDescription;
        }
        [event save];
        [weakSelf.delegate uploadManagerDidCompleteUpload:event];
    }];
    
    [manager setDataTaskDidReceiveDataBlock:^(NSURLSession * _Nonnull session, NSURLSessionDataTask * _Nonnull dataTask, NSData * _Nonnull data) {
        NSMutableData *responseData = weakSelf.responsesData[@(dataTask.taskIdentifier)];
        if (!responseData) {
            weakSelf.responsesData[@(dataTask.taskIdentifier)] = [NSMutableData dataWithData:data];
        } else {
            [responseData appendData:data];
        }
    }];
    return self;
}

-(void)addUpload:(NSMutableURLRequest *)request uploadId:(NSString*)uploadId fileURL:(NSURL *)fileURL{
    __weak FileUploader *weakSelf = self;
    [NSURLProtocol setProperty:uploadId forKey:kUploadUUIDStrPropertyKey inRequest:request];
    __block double lastProgressTimeStamp = 0;
    NSURLSessionUploadTask *uploadTask = [manager uploadTaskWithRequest:request fromFile:fileURL
                                                               progress:^(NSProgress * _Nonnull uploadProgress) {
                                                                   float roundedProgress = roundf(10 * (uploadProgress.fractionCompleted*100)) / 10.0;
                                                                   NSTimeInterval currentTimestamp = [[NSDate date] timeIntervalSince1970];
                                                                   if (currentTimestamp - lastProgressTimeStamp >= 1){
                                                                       lastProgressTimeStamp = currentTimestamp;
                                                                       [weakSelf.delegate uploadManagerDidReceieveProgress:roundedProgress forUpload:[NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:request]];
                                                                   }
                                                                   
                                                               } completionHandler:nil];
    [uploadTask resume];
    
}

-(void)removeUpload:(NSString*)uploadId{
    NSURLSessionUploadTask *correspondingTask = [[manager.uploadTasks filteredArrayUsingPredicate:
                                                  [NSPredicate predicateWithBlock:^BOOL(NSURLSessionUploadTask* task, NSDictionary *bindings) {
        NSString* currentId = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
        return [uploadId isEqualToString:currentId];
    }]] firstObject];
    [correspondingTask cancel];
}

-(void)acknowledgeEventReceived:(NSString*)eventId{
    
}
@end
