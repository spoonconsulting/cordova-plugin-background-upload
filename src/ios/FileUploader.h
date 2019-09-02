//
//  FileUploader.h
//  SharinPix
//
//  Created by Mevin Dhunnooa on 29/08/2019.
//

#import <Foundation/Foundation.h>
#import "FileUpload.h"
#import "UploadEvent.h"
#import <AFNetworking/AFNetworking.h>
NS_ASSUME_NONNULL_BEGIN
@protocol FileUploaderDelegate <NSObject>

@optional
- (void)uploadManagerDidFinishBackgroundEvents:(id)manager;
// comes directly from the background session's -URLSessionDidFinishEventsForBackgroundURLSession: callback
- (void)uploadManagerDidReceieveProgress:(float)progress forUpload:(NSString*)uploadId;
- (void)uploadManagerDidCompleteUpload:(UploadEvent*)event;
@end

@interface FileUploader : NSObject{
    NSURLSessionConfiguration* configuration;
    AFURLSessionManager *manager;
    
    
}
@property (nonatomic, strong) id<FileUploaderDelegate> delegate;
+ (instancetype)sharedInstance;
-(void)addUpload:(NSMutableURLRequest *)request uploadId:(NSString*)uploadId fileURL:(NSURL *)fileURL;
-(void)removeUpload:(NSString*)uploadId;
-(void)acknowledgeEventReceived:(NSString*)eventId;
@end




NS_ASSUME_NONNULL_END
