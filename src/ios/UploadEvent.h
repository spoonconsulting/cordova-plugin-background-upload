//
//  UploadEvent.h
//  SharinPix
//
//  Created by Mevin Dhunnooa on 02/09/2019.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface UploadEvent : NSObject{
     id _serverResponse;
}
@property (nonatomic, copy ) NSString *uploadId;
@property (nonatomic, copy ) NSString *state;
@property (nonatomic, assign    ) NSInteger      responseStatusCode;
@property (nonatomic, copy    ) NSString *              error;
@property (nonatomic, copy ) id serverResponse;

-(void)save;
-(void)destroy;
+(UploadEvent*)eventWithId:(NSString*)eventId;
@end

NS_ASSUME_NONNULL_END
