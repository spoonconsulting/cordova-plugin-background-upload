/*
 <codex/>
 */

#import "FileUploadManager.h"

#include <AssertMacros.h>

@interface FileUpload ()

// read/write versions of public properties

@property (nonatomic, assign, readwrite) FileUploadState        state;
@property (nonatomic, assign, readwrite) double                 progress;
@property (nonatomic, copy,   readwrite) NSHTTPURLResponse *    response;
@property (nonatomic, copy,   readwrite) NSError *              error;

// private methods

- (instancetype)initWithRequest:(NSURLRequest *)request
                     uploadUUID:(NSUUID *)uploadUUID
                   uploadDirURL:(NSURL *)uploadDirURL
                    originalURL:(NSURL *)originalURL
                   creationDate:(NSDate *)creationDate
                        manager:(FileUploadManager *)manager;

- (BOOL)isStateValidIncludingTask:(BOOL)includeTask;
- (BOOL)isStateValid;

// private properties, set up by init method

@property (nonatomic, copy,   readonly ) NSURL *                        uploadDirURL;
@property (nonatomic, strong, readwrite) FileUploadManager *            manager;

// other private properties

@property (nonatomic, strong, readwrite) NSURLSessionUploadTask *       task;

@end

@interface FileUploadManager () <NSURLSessionDataDelegate>

// read/write versions of public properties
@property (nonatomic, strong, readonly ) NSMutableDictionary *responsesData;
@property (nonatomic, strong, readonly ) NSMutableDictionary *          uploadsByUUID;

// private properties

@property (nonatomic, strong, readwrite) NSURLSession *                 session;

// private methods

- (void)startFileUpload:(FileUpload *)upload;
- (void)stopFileUpload:(FileUpload *)upload;
- (void)removeFileUpload:(FileUpload *)upload;

@end

@implementation FileUploadManager

static NSString * kUploadUUIDStrPropertyKey = @"com.example.apple-samplecode.BackgroundUpload.UUIDStr";

static NSString * kUploadDirectoryPrefix = @"Upload-";

static NSString * kImmutableInfoFileName      = @"ImmutableInfo.plist";
static NSString * kMutableInfoFileName        = @"MutableInfo.plist";
static NSString * kUploadContentsFileBaseName = @"UploadContents";                      // plus an optional path extension

// Keys for the "ImmutableInfo.plist" file.

static NSString * kImmutableInfoRequestDataKey = @"requestData";                        // not just the URL, but the method and headers as well
static NSString * kImmutableInfoOriginalURLDataKey = @"originalURLData";                // we archive this so that we preserve relative URL info
static NSString * kImmutableInfoCreationDateKey = @"creationDate";
static NSString * kImmutableInfoCreationDateNumKey = @"creationDateNum";
static NSString * kImmutableInfoFileId = @"fileId";

// Note: We write the creation date as a date because it's a nice, human-readable value,
// but we /read/ the creation date as a floating point number because that preserves
// sub-second accuracy.

// Keys for the "MutableInfo.plist" file.

static NSString * kMutableInfoStateKey = @"state";
static NSString * kMutableInfoResponseJsonKey = @"responseJson";
static NSString * kMutableInfoResponseDataKey = @"responseData";
static NSString * kMutableInfoErrorDataKey = @"errorData";
static NSString * kMutableInfoProgressKey = @"progress";

#pragma mark * API

+ (instancetype)sharedInstance
{
    static FileUploadManager *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        NSURL* uploadWorkDir = [[[NSFileManager defaultManager]
                                 URLForDirectory:NSCachesDirectory
                                 inDomain:NSUserDomainMask appropriateForURL:nil create:YES error:NULL] URLByAppendingPathComponent:@"FileUploadManager"];
        
        [[NSFileManager defaultManager] createDirectoryAtURL:uploadWorkDir withIntermediateDirectories:NO attributes:nil error:NULL];
        
        
        sharedInstance = [[FileUploadManager alloc] initWithWorkDirectoryURL:uploadWorkDir];

    });
    return sharedInstance;
}


- (id)initWithWorkDirectoryURL:(NSURL *)workDirectoryURL
{
    NSParameterAssert(workDirectoryURL != nil);
    self = [super init];
    if (self != nil) {
        self->_responsesData  = [[NSMutableDictionary alloc] init];
        self->_workDirectoryURL  = [workDirectoryURL copy];
        self->_uploadsByUUID   = [[NSMutableDictionary alloc] init];
        self->_sessionIdentifier = [NSString stringWithFormat:@"%@.BackgroundSession", [[NSBundle mainBundle] bundleIdentifier]];
    }
    return self;
}

// +++ Investigate the "A background URLSession with identifier xxx already exists!" that we
// sometimes see during debugging.

- (void)start
{
    NSURLSessionConfiguration *     configuration;
    
    NSParameterAssert(self.session == nil);                 // you can't start us twice
    
    [self logWithFormat:@"will start with work directory %@", self.workDirectoryURL];
    
    // Create our view of the world based on the on-disk data structures.
    
    [self restoreAllUploadsInWorkDirectory];
    
    // Create the session.  Flip YES to NO to test in a standard session.
    
    if (self.sessionIdentifier != nil) {
        configuration = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:self.sessionIdentifier];

    } else {
        configuration = [NSURLSessionConfiguration ephemeralSessionConfiguration];
    }
    
    // Allow the delegate to tweak the configuration.
    
    id<FileUploadManagerDelegate> delegate;
    delegate = self.delegate;
    if ([delegate respondsToSelector:@selector(uploadManager:willCreateSessionWithConfiguration:)]) {
        [delegate uploadManager:self willCreateSessionWithConfiguration:configuration];
    }
    
    // Create the session.
    
    self.session = [NSURLSession sessionWithConfiguration:configuration delegate:self delegateQueue:[NSOperationQueue mainQueue]];
    assert(self.session != nil);
    
    // This is where things get wacky.  From the point that we create the session (in the previous
    // line) to the point where the block passed to -getTasksWithCompletionHandler: runs, we can
    // be getting delegate callbacks for tasks whose corresponding upload objects are in the wrong
    // state (specifically, the task property isn't set and, in some cases, the state might be wrong).
    // A lot of the logic in -syncUploadTasks: and, especially -uploadForTask:, is designed to
    // compensate for that oddity.
    
    [self.session getTasksWithCompletionHandler:^(NSArray *dataTasks, NSArray *uploadTasks, NSArray *downloadTasks) {
        assert([dataTasks count] == 0);
        assert([downloadTasks count] == 0);
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            [self syncUploadTasks:uploadTasks];
        }];
    }];
    
    [self logWithFormat:@"did start"];
}

- (void)stop
{
    if (self.session != nil) {
        [self logWithFormat:@"will stop"];
        
        // Need to nil out the manager to avoid memory leaks.
        
        for (FileUpload * upload in [self.uploadsByUUID allValues]) {
            upload.manager = nil;
        }
        
        // Remove all our uploads.
        
        [self.uploadsByUUID removeAllObjects];
        
        // Kill our session.
        
        [self.session invalidateAndCancel];
        self.session = nil;
        
        [self logWithFormat:@"did stop"];
    }
}

- (NSSet *)uploads
{
    return [NSSet setWithArray:[self.uploadsByUUID allValues]];
}

-(FileUpload*) getUploadById: (NSString*)fileId
{
    for(id key in self.uploadsByUUID){
        FileUpload* upload = [self.uploadsByUUID objectForKey:key];
        if ([upload.fileId isEqualToString:fileId]){
            return upload;
        }
    }
    return nil;
    
}

- (void)addUpload:(FileUpload *)upload
{
    NSSet *         mutation;
    
    mutation = [NSSet setWithObject:upload];
    [self willChangeValueForKey:@"uploads" withSetMutation:NSKeyValueUnionSetMutation usingObjects:mutation];
    [self.uploadsByUUID setObject:upload forKey:upload.uploadUUID];
    [self  didChangeValueForKey:@"uploads" withSetMutation:NSKeyValueUnionSetMutation usingObjects:mutation];
}

- (FileUpload *)createUploadWithRequest:(NSURLRequest *)request fileId:(NSString*)fileId fileURL:(NSURL *)fileURL
{
    NSUUID *        uploadUUID;
    NSURL *         uploadDirURL;
    NSDate *        creationDate;
    FileUpload *    upload;
    
    NSParameterAssert(fileURL != nil);
    NSParameterAssert(request != nil);
    NSParameterAssert(self.session != nil);
    
    upload = nil;
    
    uploadUUID = [NSUUID UUID];
    //append the fileId at end
    uploadUUID =[[NSUUID alloc]initWithUUIDString:uploadUUID.UUIDString];
    creationDate = [NSDate date];
    
    // Create a upload directory containing our immutable info, including a hard link
    // to the file to upload.
    
    uploadDirURL = [self createImmutableInfoForOriginalURL:fileURL request:request  fileId:fileId  uploadUUID:uploadUUID creationDate:creationDate];
    
    // Create a upload object to match.
    
    if (uploadDirURL != nil) {
        
        upload = [[FileUpload alloc] initWithRequest:request uploadUUID:uploadUUID uploadDirURL:uploadDirURL originalURL:fileURL creationDate:creationDate manager:self];
        upload.fileId = fileId;
        // Add it to our uploads dictionary (and hence to the public uploads set).
        
        [self addUpload:upload];
        
        [self logWithFormat:@"did create %@ for URL %@", upload, [request URL]];
    }
    
    return upload;
}

- (void)didChangeStateForUpload:(FileUpload *)upload
{
    id<FileUploadManagerDelegate>       delegate;
    
    assert(upload != nil);
    
    delegate = self.delegate;
    if ( [delegate respondsToSelector:@selector(uploadManager:didChangeStateForUpload:)]) {
        [delegate uploadManager:self didChangeStateForUpload:upload];
    }
}

- (void)logWithFormat:(NSString *)format arguments:(va_list)arguments
{
    id<FileUploadManagerDelegate>       delegate;
    
    assert(format != nil);
    
    delegate = self.delegate;
    if ( [delegate respondsToSelector:@selector(uploadManager:logWithFormat:arguments:)]) {
        [delegate uploadManager:self logWithFormat:format arguments:arguments];
    }
}

- (void)logWithFormat:(NSString *)format, ... NS_FORMAT_FUNCTION(1,2)
{
    va_list     arguments;
    
    va_start(arguments, format);
    [self logWithFormat:format arguments:arguments];
    va_end(arguments);
}

#pragma mark * Startup

- (BOOL)restoreUploadFromUploadDirectoryURL:(NSURL *)uploadDirURL
{
    BOOL            success;
    NSUUID *        uploadUUID;
    NSDictionary *  immutableInfo;
    NSData *        requestData;
    NSURLRequest *  request;
    NSData *        originalURLData;
    NSURL *         originalURL;
    NSNumber *      creationDateNum;
    NSDate *        creationDate;
    
    // First try to restore the immutable info.
    NSString* directoryName= [uploadDirURL lastPathComponent];
    uploadUUID = [[NSUUID alloc] initWithUUIDString:[[directoryName componentsSeparatedByString:@"*"] lastObject]];
    success = (uploadUUID != nil);
    if (success) {
        immutableInfo = [[NSDictionary alloc] initWithContentsOfURL:[uploadDirURL URLByAppendingPathComponent:kImmutableInfoFileName]];
        success = (immutableInfo != nil);
    }
    if (success) {
        requestData = [immutableInfo objectForKey:kImmutableInfoRequestDataKey];
        success = [requestData isKindOfClass:[NSData class]];
    }
    if (success) {
        request = [NSKeyedUnarchiver unarchiveObjectWithData:requestData];
        success = [request isKindOfClass:[NSURLRequest class]];
    }
    if (success) {
        originalURLData = [immutableInfo objectForKey:kImmutableInfoOriginalURLDataKey];
        success = [originalURLData isKindOfClass:[NSData class]];
    }
    if (success) {
        originalURL = [NSKeyedUnarchiver unarchiveObjectWithData:originalURLData];
        success = [originalURL isKindOfClass:[NSURL class]];
    }
    if (success) {
        creationDateNum = [immutableInfo objectForKey:kImmutableInfoCreationDateNumKey];
        success = [creationDateNum isKindOfClass:[NSNumber class]];
    }
    if (success) {
        creationDate = [NSDate dateWithTimeIntervalSinceReferenceDate:[creationDateNum doubleValue]];
    }
    
    // Then restore the mutable info.  From here on we can't fail, in that if the mutable info
    // is bogus we just start the upload from scratch.
    
    if (success) {
        FileUpload *        upload;
        NSString *          mutableInfoLogStr;
        
        upload = [[FileUpload alloc] initWithRequest:request uploadUUID:uploadUUID uploadDirURL:uploadDirURL originalURL:originalURL creationDate:creationDate manager:self];
        
        // Try to restore the mutable state.  If that fails, re-create the upload
        // and let's start from scratch based on the immutable state.
        
        success = [self restoreMutableInfoForFileUpload:upload];
        if (success) {
            mutableInfoLogStr = @" (including mutable info)";
        } else {
            mutableInfoLogStr = @" (without mutable info)";
            upload = [[FileUpload alloc] initWithRequest:request uploadUUID:uploadUUID uploadDirURL:uploadDirURL originalURL:originalURL creationDate:creationDate manager:self];
            success = YES;
        }
        
        // Add it to our uploads dictionary (and hence to the public uploads set).
        
        [self addUpload:upload];
        
        [self logWithFormat:@"did restore %@%@ for %@ from %@", upload, mutableInfoLogStr, [request URL], uploadDirURL];
    } else {
        [self logWithFormat:@"did not restore from %@", uploadDirURL];
    }
    
    return success;
}

-(NSString*) getFileIdForUpload:(FileUpload*)upload{
    
    for (NSURL * itemURL in [[NSFileManager defaultManager] contentsOfDirectoryAtURL:self.workDirectoryURL includingPropertiesForKeys:@[NSURLIsDirectoryKey] options:NSDirectoryEnumerationSkipsSubdirectoryDescendants error:NULL]) {
        NSString* directoryName = [itemURL lastPathComponent];
        if ([directoryName hasPrefix:kUploadDirectoryPrefix] && [directoryName containsString:upload.uploadUUID.UUIDString]) {
            
            return [[[directoryName componentsSeparatedByString:@"*"] firstObject]
                    stringByReplacingOccurrencesOfString:kUploadDirectoryPrefix withString:@""];
        }
    }
    
    return @"";

}

- (void)restoreAllUploadsInWorkDirectory
{
    for (NSURL * itemURL in [[NSFileManager defaultManager] contentsOfDirectoryAtURL:self.workDirectoryURL includingPropertiesForKeys:@[NSURLIsDirectoryKey] options:NSDirectoryEnumerationSkipsSubdirectoryDescendants error:NULL]) {
        BOOL            success;
        NSError *       error;
        NSNumber *      isDirectory;
        
        isDirectory = nil;
        
        success = [itemURL getResourceValue:&isDirectory forKey:NSURLIsDirectoryKey error:&error];
        assert(success);
        
        if ( [isDirectory boolValue] && [[itemURL lastPathComponent] hasPrefix:kUploadDirectoryPrefix] ) {
            
            success = [self restoreUploadFromUploadDirectoryURL:itemURL];
            
            // The above only returns NO if the upload directory is completely bogus.  In that case,
            // we delete it so that it doesn't trouble us again in the future.
            
            if ( ! success ) {
                success = [[NSFileManager defaultManager] removeItemAtURL:itemURL error:&error];
                assert(success);
            }
        }
    }
}

- (FileUpload *)uploadForTask:(NSURLSessionTask *)task
{
    NSString *      uploadUUIDStr;
    NSUUID *        uploadUUID;
    FileUpload *    upload;
    
    upload = nil;
    
    // First get the UUID from the task and map that to a upload.
    //
    // This could potentially be a performance bottleneck, in which case we should
    // cache the task-to-UUID mapping in an NSMapTable ivar.
    
    if (self.session != nil) {                                                  // Ignore delegate callbacks if the session has been stopped.
        if ( ! [task isKindOfClass:[NSURLSessionUploadTask class]] ) {
            assert(NO);
        } else {
            uploadUUIDStr = [NSURLProtocol propertyForKey:kUploadUUIDStrPropertyKey inRequest:task.originalRequest];
            if (uploadUUIDStr == nil) {
                assert(NO);
            } else if ( ! [uploadUUIDStr isKindOfClass:[NSString class]] ) {
                assert(NO);
            } else {
                uploadUUID = [[NSUUID alloc] initWithUUIDString:uploadUUIDStr];
                if (uploadUUID == nil) {
                    assert(NO);
                } else {
                    upload = [self.uploadsByUUID objectForKey:uploadUUID];
                }
            }
        }
    }
    
    // If we're returning a valid upload, check that it has this task associated with it.
    
    if (upload != nil) {
        if (upload.task == nil) {
            if (upload.state == kFileUploadStateUploaded) {
                // There's no need for this task if the upload is already finished.
                // To avoid confusing our caller, we simply pretend that the task
                // doesn't match any upload.  Note that this means we end up calling
                // -cancel on the task, below, and that's the right thing to do.
                [self logWithFormat:@"unexpected task for completed %@", upload];
                upload = nil;
            } else {
                // Associate this task with this upload.
                
                assert([task isKindOfClass:[NSURLSessionUploadTask class]]);      // checked above
                upload.task = (NSURLSessionUploadTask *) task;
                
                // If we don't think the task is running, we're wrong.
                
                if (upload.state != kFileUploadStateStarted) {
                    [self logWithFormat:@"unexpected state for %@", upload];
                    upload.state = kFileUploadStateStarted;
                    [self didChangeStateForUpload:upload];
                    // We don't save the state here.  If we were to be terminated
                    // and resumed while this upload was still running, we'd simply
                    // re-do this change, which is fine.
                }
            }
        } else {
            assert(upload.task == task);
        }
        assert(upload.isStateValid);
    }
    
    // If there's no matching upload, we have no idea what this task is about
    // so we simply cancel it.
    
    if ( (upload == nil) && (task.state != NSURLSessionTaskStateCompleted) ) {
        [task cancel];
    }
    
    return upload;
}

- (NSURLSessionUploadTask *)uploadTaskForUpload:(FileUpload *)upload
{
    NSURLSessionUploadTask *    result;
    NSMutableURLRequest *       request;
    
    request = [upload.request mutableCopy];
    [NSURLProtocol setProperty:[upload.uploadUUID UUIDString] forKey:kUploadUUIDStrPropertyKey inRequest:request];
    result = [self.session uploadTaskWithRequest:request fromFile:[self uploadContentsURLForUpload:upload]];
    assert(result != nil);
    return result;
}

- (void)syncUploadTasks:(NSArray *)uploadTasks
{
    [self logWithFormat:@"will sync upload tasks"];
    
    // Call -uploadForTask: on each existing task.  This acts like there was a dummy
    // delegate callback for the task; it takes care of all the work associated with
    // reconnecting our upload to the task.
    //
    // The nice thing about this logic is that it does nothing if a real delegate callback
    // happened between -start and this method being called, so things work regardless of
    // who wins that race.
    
    for (NSURLSessionUploadTask * task in uploadTasks) {
        (void) [self uploadForTask:task];
    }
    
    // At this point we know about all the running tasks.  For each upload that thinks it's
    // started but which has no running task, start a task.  There's no point trying to resume
    // because a task in the 'started' state has no resume data.
    
    for (FileUpload * upload in [self.uploadsByUUID allValues]) {
        if ( (upload.state == kFileUploadStateStarted) && (upload.task == nil) ) {
            upload.task = [self uploadTaskForUpload:upload];
            [upload.task resume];
            [self logWithFormat:@"did start task for %@", upload];
        }
        assert([upload isStateValid]);
    };
    
    [self logWithFormat:@"did sync upload tasks"];
}

#pragma mark * Upload Directory Management

- (NSURL *)createImmutableInfoForOriginalURL:(NSURL *)originalURL request:(NSURLRequest *)request fileId:(NSString*)fileId  uploadUUID:(NSUUID *)uploadUUID creationDate:(NSDate *)creationDate
{
    BOOL            success;
    NSURL *         uploadDirURL;
    NSURL *         immutableInfoURL;
    
    assert(request != nil);
    assert(uploadUUID != nil);
    assert(creationDate != nil);
    
    // Create the upload directory as "Upload-fileId*<UUID>".
    NSString* directoryPrefix= [NSString stringWithFormat:@"%@%@*%@", kUploadDirectoryPrefix, fileId,[uploadUUID UUIDString]];
    uploadDirURL = [self.workDirectoryURL URLByAppendingPathComponent:directoryPrefix];
    success = [[NSFileManager defaultManager] createDirectoryAtURL:uploadDirURL withIntermediateDirectories:NO attributes:nil error:NULL];
    assert(success);
    
    // move file to upload in our upload directory, calling it "UploadContents" with an
    // optional extension.
    NSError* err;
    success = [[NSFileManager defaultManager] moveItemAtURL:originalURL toURL:[self uploadContentsURLForUploadDirURL:uploadDirURL originalURL:originalURL] error:&err];
    if (!success){
        NSLog(@"error: %@", err.localizedDescription);
    }
    
    // Write our immutable information to "ImmutableInfo.plist" within that directory.
    
    if (success) {
        immutableInfoURL = [uploadDirURL URLByAppendingPathComponent:kImmutableInfoFileName];
        success = [@{
                     kImmutableInfoRequestDataKey:     [NSKeyedArchiver archivedDataWithRootObject:request],
                     kImmutableInfoOriginalURLDataKey: [NSKeyedArchiver archivedDataWithRootObject:originalURL],
                     kImmutableInfoCreationDateKey:    creationDate,
                     kImmutableInfoCreationDateNumKey: @([creationDate timeIntervalSinceReferenceDate])
                     } writeToURL:immutableInfoURL atomically:YES];
    }
    
    // Clean up on error.
    
    if ( ! success ) {
        (void) [[NSFileManager defaultManager] removeItemAtURL:uploadDirURL error:NULL];
        uploadDirURL = nil;
    }
    
    return uploadDirURL;
}

- (NSURL *)uploadContentsURLForUploadDirURL:(NSURL *)uploadDirURL originalURL:(NSURL *)originalURL
{
    NSString *      uploadContentsFileName;
    
    assert(uploadDirURL != nil);
    assert(originalURL != nil);
    
    uploadContentsFileName = [kUploadContentsFileBaseName stringByAppendingPathExtension:[originalURL pathExtension]];
    
    return [uploadDirURL URLByAppendingPathComponent:uploadContentsFileName];
}

- (NSURL *)uploadContentsURLForUpload:(FileUpload *)upload
{
    assert(upload != nil);
    return [self uploadContentsURLForUploadDirURL:upload.uploadDirURL originalURL:upload.originalURL];
}

- (void)saveMutableInfoForFileUpload:(FileUpload *)upload
{
    BOOL                    success;
    NSURL *                 mutableInfoURL;
    NSMutableDictionary *   mutableInfo;
    
    [self logWithFormat:@"will save mutable info for %@", upload];
    
    // Write the mutable info to "MutableInfo.plist".
    
    mutableInfoURL = [upload.uploadDirURL URLByAppendingPathComponent:kMutableInfoFileName];
    mutableInfo = [[NSMutableDictionary alloc] init];
    [mutableInfo setObject:upload.fileId forKey:kImmutableInfoFileId];
    [mutableInfo setObject:@(upload.state) forKey:kMutableInfoStateKey];
    if (upload.response != nil) {
        [mutableInfo setObject:[NSKeyedArchiver archivedDataWithRootObject:upload.response] forKey:kMutableInfoResponseDataKey];
    }
    if (upload.serverResponse != nil) {
        [mutableInfo setObject:[NSKeyedArchiver archivedDataWithRootObject:upload.serverResponse] forKey:kMutableInfoResponseJsonKey];
    }
    if (upload.error != nil) {
        NSLog(@"Error: %@", upload.error.localizedDescription);
        [mutableInfo setObject:[NSKeyedArchiver archivedDataWithRootObject:upload.error] forKey:kMutableInfoErrorDataKey];
    }
    [mutableInfo setObject:@(upload.progress) forKey:kMutableInfoProgressKey];
    success = [mutableInfo writeToURL:mutableInfoURL atomically:YES];
    assert(success);
}

- (BOOL)restoreMutableInfoForFileUpload:(FileUpload *)upload
{
    BOOL                    success;
    NSURL *                 mutableInfoURL;
    NSDictionary *          mutableInfo;
    
    [self logWithFormat:@"will restore mutable info for %@", upload];
    
    success = NO;
    
    // Read the mutable info from "MutableInfo.plist".
    
    mutableInfoURL = [upload.uploadDirURL URLByAppendingPathComponent:kMutableInfoFileName];
    mutableInfo = [NSDictionary dictionaryWithContentsOfURL:mutableInfoURL];
    if (mutableInfo != nil) {
        NSNumber *          stateObj;
        NSData *            responseData;
        NSData *            errorData;
        NSHTTPURLResponse * response;
        NSError *           error;
        NSNumber *          progressNum;
        FileUploadState     state;
        NSString * fileId;
        
        // Extract the state.
        
        stateObj   =   [mutableInfo objectForKey:kMutableInfoStateKey];
        responseData = [mutableInfo objectForKey:kMutableInfoResponseDataKey];
        errorData  =   [mutableInfo objectForKey:kMutableInfoErrorDataKey];
        progressNum =  [mutableInfo objectForKey:kMutableInfoProgressKey];
        if ([mutableInfo objectForKey:kImmutableInfoFileId]){
            fileId =  [mutableInfo objectForKey:kImmutableInfoFileId];
        }else{
            fileId = @"";
        }
        
        
        // Do a rough validity check.
        
        success = [stateObj isKindOfClass:[NSNumber class]];
        if (success) {
            state = (FileUploadState) [stateObj unsignedIntegerValue];
            success = (state >= kFileUploadStateStopped) && (state <= kFileUploadStateFailed);
        }
        if (success && (responseData != nil)) {
            success = [responseData isKindOfClass:[NSData class]];
            if (success) {
                response = [NSKeyedUnarchiver unarchiveObjectWithData:responseData];
                success = [response isKindOfClass:[NSHTTPURLResponse class]];
            }
        }
        if (success && (errorData != nil)) {
            success = [errorData isKindOfClass:[NSData class]];
            if (success) {
                error = [NSKeyedUnarchiver unarchiveObjectWithData:errorData];
                success = [error isKindOfClass:[NSError class]];
            }
        }
        if (success) {
            success = [progressNum isKindOfClass:[NSNumber class]];
        }
        
        // Apply the state to the upload object and have it do a proper validity check.
        
        if (success) {
            upload.state = state;
            // Don't call -didChangeStateForUpload: because a) we don't know if the state
            // will 'stick', and b) this upload isn't in the public uploads set yet.
            upload.response = response;
            upload.error = error;
            upload.progress = [progressNum doubleValue];
            success = [upload isStateValidIncludingTask:NO];
            upload.fileId =fileId;
        }
    }
    return success;
}

#pragma mark * Private API for FileUpload Class

- (void)startFileUpload:(FileUpload *)upload
{
    NSURLSessionUploadTask *    task;
    
    [self logWithFormat:@"will start %@", upload];
    
    NSParameterAssert(self.session != nil);     // manager must be started
    NSParameterAssert((upload.state == kFileUploadStateStopped) || (upload.state == kFileUploadStateFailed));
    task = [self uploadTaskForUpload:upload];
    upload.task = task;
    upload.error = nil;
    upload.state = kFileUploadStateStarted;
    assert([upload isStateValid]);
    [self saveMutableInfoForFileUpload:upload];
    [self didChangeStateForUpload:upload];
    [upload.task resume];
    
    [self logWithFormat:@"did start %@", upload];
    // ... continues in -URLSession:task:didCompleteWithError:
}

- (void)stopFileUpload:(FileUpload *)upload
{
    [self logWithFormat:@"will stop %@", upload];
    
    NSParameterAssert(self.session != nil);     // manager must be started
    NSParameterAssert(upload.state == kFileUploadStateStarted);
    
    upload.state = kFileUploadStateStopping;
    assert(upload.isStateValid);
    // We don't save the state here.  We expect the -URLSession:task:didCompleteWithError:
    // to be called almost immediately, so there's little point.
    [self didChangeStateForUpload:upload];
    
    [upload.task cancel];
    [self logWithFormat:@"did stop %@", upload];
    // ... continues in -URLSession:task:didCompleteWithError:
}

- (void)removeFileUpload:(FileUpload *)upload
{
    BOOL        success;
    NSError *   error;          // just for debugging
    NSSet *     mutation;
    
    [self logWithFormat:@"will remove %@", upload];
    
    NSParameterAssert(self.session != nil);     // manager must be started
    //NSParameterAssert((upload.state == kFileUploadStateStopped) || (upload.state == kFileUploadStateUploaded) || (upload.state == kFileUploadStateFailed));
    assert([self.uploadsByUUID objectForKey:upload.uploadUUID] != nil);
    
    mutation = [NSSet setWithObject:upload];
    [self willChangeValueForKey:@"uploads" withSetMutation:NSKeyValueMinusSetMutation usingObjects:mutation];
    [self.uploadsByUUID removeObjectForKey:upload.uploadUUID];
    [self  didChangeValueForKey:@"uploads" withSetMutation:NSKeyValueMinusSetMutation usingObjects:mutation];
    
    success = [[NSFileManager defaultManager] removeItemAtURL:upload.uploadDirURL error:&error];
    assert(success);
}

#pragma mark * Delegate Callbacks
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveData:(NSData *)data {
    
    
    NSMutableData *responseData = self.responsesData[@(dataTask.taskIdentifier)];
    if (!responseData) {
        responseData = [NSMutableData dataWithData:data];
        self.responsesData[@(dataTask.taskIdentifier)] = responseData;
    } else {
        [responseData appendData:data];
    }
    
}

- (void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session
{
    [[NSOperationQueue mainQueue] addOperationWithBlock:^{
        assert( (self.session == nil) || (session == self.session) );
        if (self.session != nil) {
            id<FileUploadManagerDelegate>       delegate;
            
            delegate = self.delegate;
            if ([delegate respondsToSelector:@selector(uploadManagerDidFinishBackgroundEvents:)]) {
                [delegate uploadManagerDidFinishBackgroundEvents:self];
            }
        }
    }];
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didSendBodyData:(int64_t)bytesSent totalBytesSent:(int64_t)totalBytesSent totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend
{
#pragma unused(bytesSent)
    FileUpload *    upload;
    double          newProgress;
    
    assert(session == self.session);
    assert(totalBytesExpectedToSend != NSURLSessionTransferSizeUnknown);        // because we provided a file to upload, NSURLSession should
    // always give us meaningful progress
    upload = [self uploadForTask:task];
    if (upload != nil) {
        newProgress = (double) totalBytesSent / (double) totalBytesExpectedToSend;
        if (upload.progress != newProgress) {
            upload.progress = newProgress;
            [self didChangeStateForUpload:upload];
        }
    }
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error
{
    FileUpload *    upload;
    
    assert( (self.session == nil) || (session == self.session) );
    
    upload = [self uploadForTask:task];
    if (upload != nil) {
        assert( (upload.state == kFileUploadStateStarted) || (upload.state == kFileUploadStateStopping) );
        
        if (error == nil) {
            // The upload finished so record the completion.
            
            assert([upload.task.response isKindOfClass:[NSHTTPURLResponse class]]);
            
            upload.progress = 1.0;
            upload.response = (NSHTTPURLResponse *) upload.task.response;
            upload.error = nil;
            upload.task = nil;
            upload.state = kFileUploadStateUploaded;
           
            
            NSMutableData *responseData = self.responsesData[@(task.taskIdentifier)];
            
            if (responseData) {
                
                NSDictionary *response = [NSJSONSerialization JSONObjectWithData:responseData options:0 error:nil];
                
                if (response) {
                    upload.serverResponse = response;
                    NSLog(@"response = %@", response);
                } else {
                    upload.serverResponse =[[NSString alloc] initWithData:responseData encoding:NSUTF8StringEncoding];
                    NSLog(@"responseData = %@", upload.serverResponse);
                }
                
                [self.responsesData removeObjectForKey:@(task.taskIdentifier)];
            }
            
             [self logWithFormat:@"completed %@, finished", upload];
            
        } else if ([[error domain] isEqual:NSURLErrorDomain] && ([error code] == NSURLErrorCancelled)) {
            // The upload was stopped by us.
            
            upload.error = nil;
            upload.task = nil;
            upload.state = kFileUploadStateStopped;
            [self logWithFormat:@"completed %@, stopped", upload];
        } else {
            // The upload was stopped by the network.
            
            upload.error = error;
            upload.task = nil;
            upload.state = kFileUploadStateFailed;
            [self logWithFormat:@"completed %@, failed", upload];
        }
        [upload isStateValid];
        [self saveMutableInfoForFileUpload:upload];
        [self didChangeStateForUpload:upload];
    }
}

@end

@implementation FileUpload

- (instancetype)initWithRequest:(NSURLRequest *)request
                     uploadUUID:(NSUUID *)uploadUUID
                   uploadDirURL:(NSURL *)uploadDirURL
                    originalURL:(NSURL *)originalURL
                   creationDate:(NSDate *)creationDate
                        manager:(FileUploadManager *)manager
{
    assert(request != nil);
    assert(uploadUUID != nil);
    assert(uploadDirURL != nil);
    assert(originalURL != nil);
    assert(creationDate != nil);
    assert(manager != nil);
    self = [super init];
    if (self != nil) {
        self->_request = [request copy];
        self->_uploadUUID = [uploadUUID copy];
        self->_uploadDirURL = [uploadDirURL copy];
        self->_originalURL = [originalURL copy];
        self->_creationDate = [creationDate copy];
        self->_manager = manager;
        assert([self isStateValid]);
        self->_progress = 0.0;
    }
    return self;
}

+ (NSString *)stringForState:(FileUploadState)state
{
    static NSString * sStateNames[] = { @"stopped", @"started", @"stopping", @"uploaded", @"failed" };
    
    __Check_Compile_Time((sizeof(sStateNames) / sizeof(sStateNames[0])) == (kFileUploadStateFailed + 1));
    return sStateNames[state];
}

- (NSString *)description
{
    static NSString * sStateNames[] = { @"stopped", @"started", @"stopping", @"uploaded", @"failed" };
    
    __Check_Compile_Time((sizeof(sStateNames) / sizeof(sStateNames[0])) == (kFileUploadStateFailed + 1));
    
    return [[NSString alloc] initWithFormat:@"%@ {%@, %@}", [super description], [[self class] stringForState:self.state], [self.uploadUUID UUIDString]];
}

- (BOOL)isStateValidIncludingTask:(BOOL)includeTask
{
    BOOL        result;
    
    result = (self.request != nil);
    if (result) {
        result = (self.uploadUUID != nil);
    }
    if (result) {
        result = (self.uploadDirURL != nil);
    }
    if (result) {
        result = (self.originalURL != nil);
    }
    if (result) {
        result = (self.manager != nil);
    }
    if (result) {
        result = (self.creationDate != nil);
    }
    if (result) {
        result = (self.progress >= 0.0) && (self.progress <= 1.0);
    }
    if (result) {
        result = (self.response != nil) == (self.state == kFileUploadStateUploaded);
    }
    if (result) {
        result = (self.error != nil) == (self.state == kFileUploadStateFailed);
    }
    if (result && includeTask) {
        result = (self.task != nil) == ((self.state == kFileUploadStateStarted) || (self.state == kFileUploadStateStopping));
    }
    return result;
}

- (BOOL)isStateValid
{
    return [self isStateValidIncludingTask:YES];
}

- (void)start
{
    assert([self isStateValid]);
    [self.manager startFileUpload:self];
    assert([self isStateValid]);
}

- (void)stop
{
    assert([self isStateValid]);
    [self.manager stopFileUpload:self];
    assert([self isStateValid]);
}

- (void)remove
{
    assert([self isStateValid]);
    [self.manager removeFileUpload:self];
    self.manager = nil;
}

@end
