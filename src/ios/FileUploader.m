#import "FileUploader.h"
@interface FileUploader()
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSDate *> *uploadStartTimes;
@property (nonatomic, strong) NSMutableDictionary *responsesData;
@property (nonatomic, strong) NSMutableDictionary<NSNumber *, NSNumber *> *progressTimestamps;
@property (nonatomic, strong) NSMutableDictionary<NSNumber *, NSNumber *> *lastProgresses;
@property (nonatomic, strong) AFURLSessionManager *manager;
@end

@implementation FileUploader
static NSInteger _parallelUploadsLimit = 1;
static FileUploader *singletonObject = nil;
static NSString * kUploadUUIDStrPropertyKey = @"com.spoonconsulting.plugin-background-upload.UUID";
+(instancetype)sharedInstance{
    if (!singletonObject)
        singletonObject = [[FileUploader alloc] init];
    return singletonObject;
}

-(id)init{
    self = [super init];
    if (self == nil)
        return nil;
    [UploadEvent setupStorage];
    self.responsesData = [[NSMutableDictionary alloc] init];
    self.progressTimestamps = [[NSMutableDictionary alloc] init];
    self.lastProgresses = [[NSMutableDictionary alloc] init];
    self.uploadStartTimes = [[NSMutableDictionary alloc] init];
    NSURLSessionConfiguration* configuration = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:[[NSBundle mainBundle] bundleIdentifier]];
    configuration.HTTPMaximumConnectionsPerHost = FileUploader.parallelUploadsLimit;
    configuration.sessionSendsLaunchEvents = NO;
    self.manager = [[AFURLSessionManager alloc] initWithSessionConfiguration:configuration];
    __weak FileUploader *weakSelf = self;
    [self.manager setTaskDidCompleteBlock:^(NSURLSession * _Nonnull session, NSURLSessionTask * _Nonnull task, NSError * _Nullable error) {
        NSString* uploadId = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
 
        if (!uploadId){
            NSLog(@"[BackgroundUpload] Unknown task %@ with error %@", @(task.taskIdentifier), error);
            [weakSelf.responsesData removeObjectForKey:@(task.taskIdentifier)];
            return;
        }

        [weakSelf.progressTimestamps removeObjectForKey:@(task.taskIdentifier)];
        NSNumber *lastProgress = weakSelf.lastProgresses[@(task.taskIdentifier)];
        [weakSelf.lastProgresses removeObjectForKey:@(task.taskIdentifier)];
        NSDate *startTime;
        @synchronized (weakSelf.uploadStartTimes) {
            startTime = weakSelf.uploadStartTimes[uploadId];
            [weakSelf.uploadStartTimes removeObjectForKey:uploadId];
        }
        NSDate *endUploadTime = [NSDate date];
        NSTimeInterval timeInterval = [endUploadTime timeIntervalSince1970];
        long long endUploadTimeInMS = (long long)(timeInterval * 1000);
        NSTimeInterval duration = [endUploadTime timeIntervalSinceDate:startTime];
        NSLog(@"[BackgroundUpload] Task %@ completed with error %@", uploadId, error);
        if (!error){
            NSData* serverData = weakSelf.responsesData[@(task.taskIdentifier)];
            NSString* serverResponse = serverData ? [[NSString alloc] initWithData:serverData encoding:NSUTF8StringEncoding] : @"";
            [weakSelf.responsesData removeObjectForKey:@(task.taskIdentifier)];
            int64_t bytesSent = task.countOfBytesSent;
            int64_t totalBytes = task.countOfBytesExpectedToSend > 0 ? task.countOfBytesExpectedToSend : bytesSent;
            float finalProgress = totalBytes > 0 ? roundf(10 * (100.0 * bytesSent / totalBytes)) / 10.0 : 100;
            if (!lastProgress || finalProgress > lastProgress.floatValue) {
                [weakSelf sendEvent:@{
                    @"progress" : @(finalProgress),
                    @"bytesSent" : @(bytesSent),
                    @"totalBytes" : @(totalBytes),
                    @"id" : uploadId,
                    @"state": @"UPLOADING"
                }];
            }
            NSMutableDictionary *event = [@{
                    @"id" : uploadId,
                    @"state" : @"UPLOADED",
                    @"statusCode" : @(((NSHTTPURLResponse *)task.response).statusCode),
                    @"serverResponse" : serverResponse
                } mutableCopy];
                
                if (!isnan(duration)) {
                    event[@"uploadDuration"] = @(duration * 1000);
                    event[@"finishUploadTime"] = @(endUploadTimeInMS);
                }
                
                [weakSelf saveAndSendEvent:event];
        } else {
            [weakSelf.responsesData removeObjectForKey:@(task.taskIdentifier)];
            [weakSelf saveAndSendEvent:@{
                @"id" : uploadId,
                @"state" : @"FAILED",
                @"error" : error.localizedDescription,
                @"errorCode" : @(error.code),
            }];
        }
    }];

    [self.manager setDataTaskDidReceiveDataBlock:^(NSURLSession * _Nonnull session, NSURLSessionDataTask * _Nonnull dataTask, NSData * _Nonnull data) {
        NSMutableData *responseData = weakSelf.responsesData[@(dataTask.taskIdentifier)];
        if (!responseData) {
            weakSelf.responsesData[@(dataTask.taskIdentifier)] = [NSMutableData dataWithData:data];
        } else {
            [responseData appendData:data];
        }
    }];

    [self.manager setTaskDidSendBodyDataBlock:^(NSURLSession * _Nonnull session, NSURLSessionTask * _Nonnull task, int64_t bytesSent, int64_t totalBytesSent, int64_t totalBytesExpectedToSend) {
        NSString* uploadId = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
        if (!uploadId || totalBytesExpectedToSend <= 0)
            return;
        BOOL allSent = totalBytesSent >= totalBytesExpectedToSend;
        NSTimeInterval currentTimestamp = [[NSDate date] timeIntervalSince1970];
        NSNumber *lastProgressTimestamp = weakSelf.progressTimestamps[@(task.taskIdentifier)];
        if (!allSent && lastProgressTimestamp && currentTimestamp - lastProgressTimestamp.doubleValue < 1)
            return;
        weakSelf.progressTimestamps[@(task.taskIdentifier)] = @(currentTimestamp);
        float roundedProgress = roundf(10 * (100.0 * totalBytesSent / totalBytesExpectedToSend)) / 10.0;
        weakSelf.lastProgresses[@(task.taskIdentifier)] = @(roundedProgress);
        NSLog(@"[BackgroundUpload] Task %@ progression %.1f%% (%lld / %lld bytes)", uploadId, roundedProgress, totalBytesSent, totalBytesExpectedToSend);
        [weakSelf sendEvent:@{
            @"progress" : @(roundedProgress),
            @"bytesSent" : @(totalBytesSent),
            @"totalBytes" : @(totalBytesExpectedToSend),
            @"id" : uploadId,
            @"state": @"UPLOADING"
        }];
    }];
    return self;
}

-(void)saveAndSendEvent:(NSDictionary*)data{
    UploadEvent* event = [UploadEvent create:data];
    [self sendEvent:[event dataRepresentation]];
}

-(void)sendEvent:(NSDictionary*)info{
    [self.delegate uploadManagerDidReceiveCallback:info];
}

+(NSInteger)parallelUploadsLimit {
    return _parallelUploadsLimit;
}

+(void)setParallelUploadsLimit:(NSInteger)value {
    _parallelUploadsLimit = value;
}

-(void)addUpload:(NSDictionary *)payload completionHandler:(void (^)(NSError* error))handler{
    __weak FileUploader *weakSelf = self;
    NSURL *tempFilePath = [self tempFilePathForUpload:payload[@"id"]];
    [self writeMultipartDataToTempFile:tempFilePath
                                   url:[NSURL URLWithString:payload[@"serverUrl"]]
                              uploadId:payload[@"id"]
                               fileURL:[NSURL fileURLWithPath:payload[@"filePath"]]
                               headers: payload[@"headers"]
                            parameters:payload[@"parameters"]
                               fileKey:payload[@"fileKey"]
                     completionHandler:^(NSError *error, NSMutableURLRequest *request) {
        if (error){
            [[NSFileManager defaultManager] removeItemAtURL:tempFilePath error:nil];
            return handler(error);
        }

        @synchronized (weakSelf.uploadStartTimes) {
            weakSelf.uploadStartTimes[payload[@"id"]] = [NSDate date];
        }

        [[weakSelf.manager uploadTaskWithRequest:request
                                        fromFile:tempFilePath
                                        progress:nil
                               completionHandler:nil] resume];
        [[NSFileManager defaultManager] removeItemAtURL:[weakSelf tempFilePathForUpload:payload[@"id"]] error:nil];
    }];
}

-(NSURL*)tempFilePathForUpload:(NSString*)uploadId{
    NSString* path = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES)[0];
    return [NSURL fileURLWithPath:[path stringByAppendingPathComponent:[NSString stringWithFormat:@"%@.request",uploadId]]];
}

-(void)writeMultipartDataToTempFile: (NSURL*)tempFilePath
                                url:(NSURL *)url
                           uploadId:(NSString*)uploadId
                            fileURL:(NSURL *)fileURL
                            headers:(NSDictionary*)headers
                         parameters:(NSDictionary*)parameters
                            fileKey:(NSString*)fileKey
                  completionHandler:(void (^)(NSError* error, NSMutableURLRequest* request))handler{
    AFHTTPRequestSerializer *serializer = [AFHTTPRequestSerializer serializer];
    NSError *error;
    __block NSError *fileError;
    NSMutableURLRequest *request =
    [serializer multipartFormRequestWithMethod:@"POST"
                                     URLString:url.absoluteString
                                    parameters:parameters
                     constructingBodyWithBlock:^(id<AFMultipartFormData> formData)
     {
        NSString *filename = [fileURL.absoluteString lastPathComponent];
        NSError *appendError;
        if (![formData appendPartWithFileURL:fileURL name:fileKey fileName:filename mimeType:@"application/octet-stream" error:&appendError])
            fileError = appendError ?: [NSError errorWithDomain:NSCocoaErrorDomain code:NSFileReadUnknownError userInfo:nil];
    }
                                         error:&error];
    if (error || fileError)
        return handler(error ?: fileError, nil);
    for (NSString *key in headers) {
        [request setValue:[headers objectForKey:key] forHTTPHeaderField:key];
    }
    [NSURLProtocol setProperty:uploadId forKey:kUploadUUIDStrPropertyKey inRequest:request];
    [self writeBodyStream:request.HTTPBodyStream toFile:tempFilePath completionHandler:^(NSError *error) {
        if (!error && ![[NSFileManager defaultManager] fileExistsAtPath:tempFilePath.path])
            error = [NSError errorWithDomain:NSCocoaErrorDomain code:NSFileReadNoSuchFileError userInfo:nil];
        NSString *expectedSize = [request valueForHTTPHeaderField:@"Content-Length"];
        NSNumber *writtenSize = [[NSFileManager defaultManager] attributesOfItemAtPath:tempFilePath.path error:nil][NSFileSize];
        if (!error && expectedSize && writtenSize.longLongValue != expectedSize.longLongValue)
            error = [NSError errorWithDomain:NSCocoaErrorDomain code:NSFileReadUnknownError userInfo:nil];
        handler(error, request);
    }];
}

-(void)writeBodyStream:(NSInputStream*)inputStream toFile:(NSURL*)fileURL completionHandler:(void (^)(NSError* error))handler{
    static const NSUInteger bufferSize = 1024 * 1024;
    uint8_t *buffer = malloc(bufferSize);
    NSOutputStream *outputStream = [[NSOutputStream alloc] initWithURL:fileURL append:NO];
    NSError *error;
    [inputStream open];
    [outputStream open];
    while (!error) {
        NSInteger bytesRead = [inputStream read:buffer maxLength:bufferSize];
        if (bytesRead < 0 || inputStream.streamError)
            error = inputStream.streamError ?: [NSError errorWithDomain:NSCocoaErrorDomain code:NSFileReadUnknownError userInfo:nil];
        if (error || bytesRead <= 0)
            break;
        NSInteger offset = 0;
        while (offset < bytesRead) {
            NSInteger bytesWritten = [outputStream write:buffer + offset maxLength:(NSUInteger)(bytesRead - offset)];
            if (bytesWritten <= 0) {
                error = outputStream.streamError ?: [NSError errorWithDomain:NSCocoaErrorDomain code:NSFileWriteUnknownError userInfo:nil];
                break;
            }
            offset += bytesWritten;
        }
    }
    [outputStream close];
    [inputStream close];
    free(buffer);
    handler(error);
}

-(void)removeUpload:(NSString*)uploadId{
    NSURLSessionUploadTask *correspondingTask =
    [[self.manager.uploadTasks filteredArrayUsingPredicate: [NSPredicate predicateWithBlock:^BOOL(NSURLSessionUploadTask* task, NSDictionary *bindings) {
        NSString* currentId = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
        return [uploadId isEqualToString:currentId];
    }]] firstObject];
    [correspondingTask cancel];
}

-(void)acknowledgeEventReceived:(NSString*)eventId{
    [[UploadEvent eventWithId:eventId] destroy];
}
@end
