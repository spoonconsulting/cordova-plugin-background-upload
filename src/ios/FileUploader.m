//
//  FileUploader.m
//  SharinPix
//
//  Created by Mevin Dhunnooa on 29/08/2019.
//

#import "FileUploader.h"
#import "AppDelegate+upload.h"
@interface FileUploader()
@property (nonatomic, strong) NSMutableDictionary* responsesData;
@property (nonatomic, strong) AFURLSessionManager *manager;
@end
@implementation FileUploader
static FileUploader *singletonObject = nil;
+ (instancetype)sharedInstance{
    if (!singletonObject)
        singletonObject = [[FileUploader alloc] init];
    return singletonObject;
}
- (id)init{
    self = [super init];
    if (self == nil)
        return nil;
    [UploadEvent setupStorage];
    self.responsesData = [[NSMutableDictionary alloc] init];
    configuration = [self.delegate uploadManagerWillExtendSessionConfiguration: [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:@"com.spoon.BackgroundUpload.session"]];
    self.manager = [[AFURLSessionManager alloc] initWithSessionConfiguration:configuration];
    __weak FileUploader *weakSelf = self;
    [self.manager setTaskDidCompleteBlock:^(NSURLSession * _Nonnull session, NSURLSessionTask * _Nonnull task, NSError * _Nullable error) {
        if ([error code] == NSURLErrorCancelled)
            return;
        UploadEvent* event = [[UploadEvent alloc] init];
        event.uploadId = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
        if (!error){
            event.state = @"SUCCESS";
            event.responseStatusCode = ((NSHTTPURLResponse *)task.response).statusCode;
            NSData* serverData = weakSelf.responsesData[@(task.taskIdentifier)];
            event.serverResponse = serverData ? [[NSString alloc] initWithData:serverData encoding:NSUTF8StringEncoding] : @"";
            [weakSelf.responsesData removeObjectForKey:@(task.taskIdentifier)];
        } else {
            event.state = @"FAILED";
            event.error = error.localizedDescription;
        }
        NSLog(@"[CD]task did complete %@", event.uploadId);
        [event save];
        [weakSelf.delegate uploadManagerDidCompleteUpload:event];
    }];
    
    [self.manager setDataTaskDidReceiveDataBlock:^(NSURLSession * _Nonnull session, NSURLSessionDataTask * _Nonnull dataTask, NSData * _Nonnull data) {
        NSMutableData *responseData = weakSelf.responsesData[@(dataTask.taskIdentifier)];
        if (!responseData) {
            weakSelf.responsesData[@(dataTask.taskIdentifier)] = [NSMutableData dataWithData:data];
        } else {
            [responseData appendData:data];
        }
    }];
    
    [self.manager setDidFinishEventsForBackgroundURLSessionBlock:^(NSURLSession * _Nonnull session) {
        AppDelegate *appDelegate = (AppDelegate *)[[UIApplication sharedApplication] delegate];
        if (appDelegate.backgroundCompletionBlock) {
            void (^completionHandler)(void) = appDelegate.backgroundCompletionBlock;
            appDelegate.backgroundCompletionBlock = nil;
            completionHandler();
        }
    }];
    return self;
}

-(void)addUpload:(NSURL *)url uploadId:(NSString*)uploadId fileURL:(NSURL *)fileURL
         headers:(NSDictionary*)headers parameters:(NSDictionary*)parameters fileKey:(NSString*)fileKey onCompletion:(void (^)(NSError* error))handler{
    NSURL *tempFilePath =  [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:uploadId]];
    AFHTTPRequestSerializer *serializer = [AFHTTPRequestSerializer serializer];
    NSError *error;
    NSMutableURLRequest *request =
    [serializer multipartFormRequestWithMethod:@"POST" URLString:url.absoluteString
                                    parameters:parameters
                     constructingBodyWithBlock:^(id<AFMultipartFormData> formData){
                         NSString *filename = [fileURL.absoluteString lastPathComponent];
                         NSData * data = [NSData dataWithContentsOfURL:fileURL];
                         [formData appendPartWithFileData:data name:fileKey fileName:filename mimeType:@"application/octet-stream"];
                     }
                                         error:&error];
    if (error)
        return handler(error);
    for (NSString *key in headers) {
        [request setValue:[headers objectForKey:key] forHTTPHeaderField:key];
    }
    [NSURLProtocol setProperty:uploadId forKey:kUploadUUIDStrPropertyKey inRequest:request];
    __weak FileUploader *weakSelf = self;
    [serializer requestWithMultipartFormRequest:request writingStreamContentsToFile:tempFilePath completionHandler:^(NSError *error) {
        if (error)
            return handler(error);
        __block double lastProgressTimeStamp = 0;
        NSURLSessionUploadTask *uploadTask =
        [weakSelf.manager uploadTaskWithRequest:request fromFile:tempFilePath
                                       progress:^(NSProgress * _Nonnull uploadProgress) {
                                           float roundedProgress = roundf(10 * (uploadProgress.fractionCompleted*100)) / 10.0;
                                           NSTimeInterval currentTimestamp = [[NSDate date] timeIntervalSince1970];
                                           if (currentTimestamp - lastProgressTimeStamp >= 1){
                                               lastProgressTimeStamp = currentTimestamp;
                                               [weakSelf.delegate uploadManagerDidReceieveProgress:roundedProgress forUpload:[NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:request]];
                                           }
                                           
                                       } completionHandler:nil];
        [uploadTask resume];
    }];
}

-(void)removeUpload:(NSString*)uploadId{
    NSURLSessionUploadTask *correspondingTask =
    [[self.manager.uploadTasks filteredArrayUsingPredicate: [NSPredicate predicateWithBlock:^BOOL(NSURLSessionUploadTask* task, NSDictionary *bindings) {
        NSString* currentId = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
        return [uploadId isEqualToString:currentId];
    }]] firstObject];
    [correspondingTask cancel];
    [[NSFileManager defaultManager] removeItemAtURL:[NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:uploadId]] error:nil];
}

-(void)acknowledgeEventReceived:(NSString*)eventId{
    [[UploadEvent eventWithId:eventId] destroy];
}
@end
