//
//  FileUpload.h
//  SharinPix
//
//  Created by Mevin Dhunnooa on 29/08/2019.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

enum FileUploadState {
    kFileUploadStateStopped,
    kFileUploadStateStarted,
    kFileUploadStateStopping,
    kFileUploadStateUploaded,
    kFileUploadStateFailed
};
typedef enum FileUploadState FileUploadState;


static NSString * kUploadUUIDStrPropertyKey = @"com.spoon.BackgroundUpload.fileId";

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

@interface FileUpload : NSObject{
    id _serverResponse;
}

@property (nonatomic, assign  ) FileUploadState        state;              // observable
@property (nonatomic, copy   ) NSUUID *               uploadUUID;         // immutable
@property (nonatomic, copy    ) NSURLRequest *         request;            // immutable
@property (nonatomic, copy    ) NSURL *                originalURL;        // immutable
@property (nonatomic, copy    ) NSDate *               creationDate;       // immutable
@property (nonatomic, assign  ) double                 progress;           // 0..1
@property (nonatomic, assign    ) NSInteger      responseStatusCode;
@property (nonatomic, copy    ) NSError *              error;
@property (nonatomic, copy ) id serverResponse;
@property (nonatomic, copy ) NSString *              fileId;

- (instancetype)initWithRequest:(NSURLRequest *)request
                         fileId:(NSString *)uploadUUID
                    originalURL:(NSURL *)originalURL;

- (BOOL)isStateValidIncludingTask:(BOOL)includeTask;
- (BOOL)isStateValid;

// private properties, set up by init method

@property (nonatomic, copy,    ) NSURL *                        uploadDirURL;


// other private properties

@property (nonatomic, strong, readwrite) NSURLSessionUploadTask *       task;
- (void)start;      // state must be kFileUploadStateStopped or kFileUploadStateFailed
- (void)stop;       // state must be kFileUploadStateStarted
- (void)remove;     // state must be kFileUploadStateStopped, kFileUploadStateUploaded or kFileUploadStateFailed

+ (NSString *)stringForState:(FileUploadState)state;

@end

NS_ASSUME_NONNULL_END
