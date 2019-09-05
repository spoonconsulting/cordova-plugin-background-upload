//
//  UploadEvent.h
//  SharinPix
//
//  Created by Mevin Dhunnooa on 02/09/2019.
//

#import <Foundation/Foundation.h>
#import <CoreData/CoreData.h>
NS_ASSUME_NONNULL_BEGIN

@interface UploadEvent : NSManagedObject{
    
}
@property (nonatomic, copy) NSString *data;
@property (nonatomic, copy) NSString *uploadId;
@property (nonatomic, copy) NSString *state;
@property (nonatomic, assign) NSInteger responseStatusCode;
@property (nonatomic, copy) NSString *error;
@property (nonatomic, copy) id serverResponse;


-(void)save;
-(void)destroy;
+(UploadEvent*)eventWithId:(NSString*)eventId;
+(NSArray*)allEvents;
+(void)setupStorage;
@end

NS_ASSUME_NONNULL_END
