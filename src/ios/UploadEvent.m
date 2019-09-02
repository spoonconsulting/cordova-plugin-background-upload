
//
//  UploadEvent.m
//  SharinPix
//
//  Created by Mevin Dhunnooa on 02/09/2019.
//

#import "UploadEvent.h"

@implementation UploadEvent
+(UploadEvent*)eventWithId:(NSString*)eventId{
    return nil;
    
}
-(void)save{
    
}

-(void)destroy{
    
}

-(void)setServerResponse:(NSMutableData*)responseData{
    if (responseData) {
        NSDictionary *response = [NSJSONSerialization JSONObjectWithData:responseData options:0 error:nil];
        if (response) {
            _serverResponse = response;
            NSLog(@"response = %@", response);
        } else {
            _serverResponse = [[NSString alloc] initWithData:responseData encoding:NSUTF8StringEncoding];
            NSLog(@"responseData = %@", _serverResponse);
        }
    }
}
-(id)serverResponse{
    return _serverResponse;
}
@end
