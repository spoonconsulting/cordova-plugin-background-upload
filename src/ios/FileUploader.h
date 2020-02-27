#import <Foundation/Foundation.h>
#import "UploadEvent.h"
#import <AFNetworking/AFNetworking.h>
NS_ASSUME_NONNULL_BEGIN
@protocol FileUploaderDelegate <NSObject>
@optional
-(void)uploadManagerDidReceiveCallback:(NSDictionary*)info;
@end

@interface FileUploader : NSObject
@property (nonatomic, strong) id<FileUploaderDelegate> delegate;
+(instancetype)sharedInstance;
-(void)addUpload:(NSDictionary *)payload completionHandler:(void (^)(NSError* error))handler;
-(void)removeUpload:(NSString*)uploadId;
-(void)acknowledgeEventReceived:(NSString*)eventId;
@property (class, nonatomic, assign) NSInteger parallelUploadsLimit;
@end

NS_ASSUME_NONNULL_END
