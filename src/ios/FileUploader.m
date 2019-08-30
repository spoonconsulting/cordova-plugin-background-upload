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
    if (self != nil) {
        
        currentUploads = [[NSMutableArray alloc] init];
        self.responsesData = [[NSMutableDictionary alloc] init];
        configuration = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:@"com.spoon.BackgroundUpload.session"];
        configuration.HTTPMaximumConnectionsPerHost = 1;
        
        manager = [[AFURLSessionManager alloc] initWithSessionConfiguration:configuration];
        __weak FileUploader *weakSelf = self;
        [manager setTaskDidCompleteBlock:^(NSURLSession * _Nonnull session, NSURLSessionTask * _Nonnull task, NSError * _Nullable error) {
            FileUpload* upload = [weakSelf getUploadById:[NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest]];
            if (!upload)
                return;
            if (!error){
                upload.responseStatusCode = ((NSHTTPURLResponse *)task.response).statusCode;
                upload.state = kFileUploadStateUploaded;
                upload.serverResponse = weakSelf.responsesData[@(task.taskIdentifier)];
                [weakSelf.responsesData removeObjectForKey:@(task.taskIdentifier)];
            } else if ([[error domain] isEqual:NSURLErrorDomain] && ([error code] == NSURLErrorCancelled)) {
                // The upload was stopped by us.
                upload.state = kFileUploadStateStopped;
            } else {
                // The upload was stopped by the network.
                upload.error = error;
                upload.state = kFileUploadStateFailed;
                
            }
            [weakSelf.delegate uploadManager:weakSelf didChangeStateForUpload:upload];
        }];
        
        [manager setDataTaskDidReceiveDataBlock:^(NSURLSession * _Nonnull session, NSURLSessionDataTask * _Nonnull dataTask, NSData * _Nonnull data) {
            NSMutableData *responseData = weakSelf.responsesData[@(dataTask.taskIdentifier)];
            if (!responseData) {
                responseData = [NSMutableData dataWithData:data];
                weakSelf.responsesData[@(dataTask.taskIdentifier)] = responseData;
            } else {
                [responseData appendData:data];
            }
        }];
    }
    return self;
}

-(FileUpload*)getUploadById:(NSString*)taskFileId{
    //manager.uploadTasks cannot be called from the setTaskDidCompleteBlock block since it cause a deadlock
    //https://stackoverflow.com/questions/31944465/afnetworking-deadlock-on-tasks-tasksforkeypath
    //use currentUploads as a workaround
    NSLog(@"AFPlug getUploadById %@ %@", taskFileId, currentUploads);
    
    NSArray *filteredArray = [currentUploads
                              filteredArrayUsingPredicate:[NSPredicate predicateWithBlock:
                                                           ^BOOL(FileUpload* currentUpload, NSDictionary *bindings) {
                                                               return [taskFileId isEqualToString:currentUpload.fileId];
                                                           }]];
//    NSLog(@"AFPlug got manager.uploadTasks %@", manager.uploadTasks);
    return filteredArray.firstObject;
}
-(void)addUpload:(FileUpload*)upload{
    //write serialized upload on disk
    //start upload
    if ([self getUploadById: upload.fileId]){
        //duplicate upload
        return;
    }
    [currentUploads addObject:upload];
    __weak FileUploader *weakSelf = self;
    NSURLSessionUploadTask *uploadTask = [manager uploadTaskWithRequest:upload.request fromFile:upload.originalURL
                                                               progress:^(NSProgress * _Nonnull uploadProgress) {
                                                                   upload.progress = uploadProgress.fractionCompleted;
                                                                   [weakSelf.delegate uploadManager:weakSelf didChangeStateForUpload:upload];
                                                               } completionHandler:nil];
    [uploadTask resume];
    
}

-(void)removeUpload:(FileUpload*)upload{
    //stop upload
    //remove serialized file
}

- (FileUpload *)createUploadWithRequest:(NSURLRequest *)request fileId:(NSString*)fileId fileURL:(NSURL *)fileURL{
    NSMutableURLRequest* mutableRequest = [request mutableCopy];
    [NSURLProtocol setProperty:fileId forKey:kUploadUUIDStrPropertyKey inRequest:mutableRequest];
    FileUpload * upload = [[FileUpload alloc] initWithRequest:[mutableRequest copy] fileId:fileId originalURL:fileURL];
    [self addUpload:upload];
    return upload;
}

@end
