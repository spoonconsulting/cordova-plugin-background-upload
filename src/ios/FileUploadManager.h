/*
 <codex>
 <abstract>Uses NSURLSession to run uploads in the background.</abstract>
 </codex>
 */

#import <Foundation/Foundation.h>

@class FileUpload;
@protocol FileUploadManagerDelegate;

@interface FileUploadManager : NSObject

+ (instancetype)sharedInstance;

@property (nonatomic, copy,   readonly ) NSURL *        workDirectoryURL;

@property (nonatomic, copy,   readonly ) NSSet *        uploads;          // of FileUpload, observable

// properties settable before calling -start

@property (nonatomic, copy,   readwrite) NSString *     sessionIdentifier;  // defaults to a value based on your bundle identifier; set to nil 
                                                                            // to use an ephemeral session; customise the configuration via the 
                                                                            // -uploadManager:willCreateSessionWithConfiguration: delegate callback

// properties settable at any time

@property (nonatomic, weak,   readwrite) id<FileUploadManagerDelegate>      delegate;

// main API

- (void)start;
-(FileUpload*) getUploadById: (NSString*)fileId;
- (void)stop;
    // WARNING: This method is here to support unit test and its semantics 
    // have not been well thought out.  It's not that implementing such a method 
    // is impossible, it's just that it requires careful thought and that's 
    // one of the corners I cut to get this stuff out the door.  Outside of 
    // unit tests, you should design your app so that the FileUploadManager 
    // object exists for the lifetime of the app, at least for the moment.

- (FileUpload *)createUploadWithRequest:(NSURLRequest *)request fileId:(NSString*)fileId fileURL:(NSURL *)fileURL;
    // upload's initial state is kFileUploadStateStopped
-(NSString*) getFileIdForUpload:(FileUpload*)upload;

@end

enum FileUploadState {
    kFileUploadStateStopped, 
    kFileUploadStateStarted, 
    kFileUploadStateStopping, 
    kFileUploadStateUploaded, 
    kFileUploadStateFailed
};
typedef enum FileUploadState FileUploadState;

@interface FileUpload : NSObject

@property (nonatomic, assign, readonly ) FileUploadState        state;              // observable
@property (nonatomic, copy,   readonly ) NSUUID *               uploadUUID;         // immutable
@property (nonatomic, copy,   readonly ) NSURLRequest *         request;            // immutable
@property (nonatomic, copy,   readonly ) NSURL *                originalURL;        // immutable
@property (nonatomic, copy,   readonly ) NSDate *               creationDate;       // immutable
@property (nonatomic, assign, readonly ) double                 progress;           // 0..1
@property (nonatomic, copy,   readonly ) NSHTTPURLResponse *    response;
@property (nonatomic, copy,   readonly ) NSError *              error;
@property (nonatomic, copy ) id          serverResponse;
@property (nonatomic, copy ) NSString *              fileId;

- (void)start;      // state must be kFileUploadStateStopped or kFileUploadStateFailed
- (void)stop;       // state must be kFileUploadStateStarted
- (void)remove;     // state must be kFileUploadStateStopped, kFileUploadStateUploaded or kFileUploadStateFailed

+ (NSString *)stringForState:(FileUploadState)state;

@end

@protocol FileUploadManagerDelegate <NSObject>

@optional

- (void)uploadManager:(FileUploadManager *)manager willCreateSessionWithConfiguration:(NSURLSessionConfiguration *)configuration;
    // allows you to override configuration parameters

- (void)uploadManagerDidFinishBackgroundEvents:(FileUploadManager *)manager;
    // comes directly from the background session's -URLSessionDidFinishEventsForBackgroundURLSession: callback

- (void)uploadManager:(FileUploadManager *)manager didChangeStateForUpload:(FileUpload *)upload;

- (void)uploadManager:(FileUploadManager *)manager logWithFormat:(NSString *)format arguments:(va_list)arguments;

@end
