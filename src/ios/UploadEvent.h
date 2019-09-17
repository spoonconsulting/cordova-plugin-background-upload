#import <Foundation/Foundation.h>
#import <CoreData/CoreData.h>
NS_ASSUME_NONNULL_BEGIN

@interface UploadEvent : NSManagedObject
@property (nonatomic, copy) NSString *data;
@property (nonatomic, copy) NSString *uploadId;
@property (nonatomic, copy) NSString *state;
@property (nonatomic, assign) NSInteger statusCode;
@property (nonatomic, copy) NSString *error;
@property (nonatomic, assign) NSInteger errorCode;
@property (nonatomic, copy) id serverResponse;

-(void)save;
-(void)destroy;
+(UploadEvent*)eventWithId:(NSString*)eventId;
+(NSArray*)allEvents;
+(void)setupStorage;
@end

NS_ASSUME_NONNULL_END
