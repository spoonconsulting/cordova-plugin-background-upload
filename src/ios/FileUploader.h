//
//  FileUploader.h
//  SharinPix
//
//  Created by Mevin Dhunnooa on 29/08/2019.
//

#import <Foundation/Foundation.h>
#import "FileUpload.h"
#import <AFNetworking/AFNetworking.h>
NS_ASSUME_NONNULL_BEGIN
@protocol FileUploaderDelegate <NSObject>

@optional
- (void)uploadManagerDidFinishBackgroundEvents:(id)manager;
// comes directly from the background session's -URLSessionDidFinishEventsForBackgroundURLSession: callback
- (void)uploadManager:(id)manager didChangeStateForUpload:(FileUpload *)upload;
- (void)uploadManager:(id)manager logWithFormat:(NSString *)format arguments:(va_list)arguments;

@end

@interface FileUploader : NSObject{
    NSURLSessionConfiguration* configuration;
    AFURLSessionManager *manager;
    
    
}
@property (nonatomic, strong) id<FileUploaderDelegate> delegate;
+ (instancetype)sharedInstance;
-(void)addUpload:(FileUpload*)upload;
- (FileUpload *)createUploadWithRequest:(NSURLRequest *)request fileId:(NSString*)fileId fileURL:(NSURL *)fileURL;
@end




NS_ASSUME_NONNULL_END
