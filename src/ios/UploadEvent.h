#import <Foundation/Foundation.h>
#import <CoreData/CoreData.h>
NS_ASSUME_NONNULL_BEGIN

@interface UploadEvent : NSManagedObject

-(void)save;
-(void)destroy;
+(UploadEvent*)eventWithId:(NSString*)eventId;
+(NSArray*)allEvents;
+(void)setupStorage;
+(UploadEvent*)create:(NSDictionary*)info;
-(NSDictionary*)dataRepresentation;
@end

NS_ASSUME_NONNULL_END
