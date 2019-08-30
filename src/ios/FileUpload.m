//
//  FileUpload.m
//  SharinPix
//
//  Created by Mevin Dhunnooa on 29/08/2019.
//

#import "FileUpload.h"


@implementation FileUpload

- (instancetype)initWithRequest:(NSURLRequest *)request
                         fileId:(NSString *)uploadUUID
                    originalURL:(NSURL *)originalURL{
    //    assert(request != nil);
    //    assert(uploadUUID != nil);
    //    assert(uploadDirURL != nil);
    //    assert(originalURL != nil);
    //    assert(creationDate != nil);
    //    assert(manager != nil);
    self = [super init];
    if (self != nil) {
        self.request = request;
        self.originalURL = originalURL;
        self.fileId = uploadUUID;
        self.progress = 0.0;
        self.state = kFileUploadStateStarted;
    }
    return self;
}



- (void)start
{
    //    assert([self isStateValid]);
    //    [self.manager startFileUpload:self];
    //    assert([self isStateValid]);
}

- (void)stop
{
    //    assert([self isStateValid]);
    //    [self.manager stopFileUpload:self];
    //    assert([self isStateValid]);
}

- (void)remove
{
    //    assert([self isStateValid]);
    //    [self.manager removeFileUpload:self];
    //    self.manager = nil;
}

-(NSDictionary*)serializedData{
    return @{};
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
