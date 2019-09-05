
//
//  UploadEvent.m
//  SharinPix
//
//  Created by Mevin Dhunnooa on 02/09/2019.
//

#import "UploadEvent.h"
@implementation UploadEvent
@synthesize error, state, responseStatusCode, serverResponse, uploadId, data;
static NSManagedObjectContext * managedObjectContext;
static NSPersistentStoreCoordinator * persistentStoreCoordinator;
- (id)init{
    NSEntityDescription *entity = [NSEntityDescription entityForName:@"UploadEvent" inManagedObjectContext:managedObjectContext];
    self = [super initWithEntity:entity insertIntoManagedObjectContext:managedObjectContext];
    if (self == nil)
        return nil;
    self.error = @"";
    return self;
}

-(void)save{
    [managedObjectContext performBlockAndWait:^{
        self.data = [self dataDescription];
        NSLog(@"[CD]saved %@",self.objectID);
        [managedObjectContext save:nil];
    }];
}

-(void)destroy{
    NSLog(@"[CD]deleting UploadEvent for %@ %@",self.uploadId, self.objectID);
    [managedObjectContext performBlock:^{
        [managedObjectContext deleteObject:self];
        [managedObjectContext save:nil];
    }];
}

-(NSString*)dataDescription{
    NSDictionary* representation = @{
                                     @"state": self.state,
                                     @"responseStatusCode": @(self.responseStatusCode),
                                     @"serverResponse": self.serverResponse,
                                     @"uploadId": uploadId,
                                     @"error": self.error
                                     };
    NSData * jsonData = [NSJSONSerialization dataWithJSONObject:representation options:0 error:nil];
    return [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
}

+(UploadEvent*)eventWithId:(NSString*)eventId{
    NSManagedObjectID* objectId = [persistentStoreCoordinator managedObjectIDForURIRepresentation: [NSURL URLWithString:eventId]];
    return [managedObjectContext objectWithID:objectId];
}

+(NSArray*)allEvents{
    NSFetchRequest* request = [NSFetchRequest fetchRequestWithEntityName:@"UploadEvent"];
    request.returnsObjectsAsFaults = NO;
    NSArray* events = [managedObjectContext executeFetchRequest:request error:NULL];
    for (UploadEvent* event in events){
        NSData *data = [event.data dataUsingEncoding:NSUTF8StringEncoding];
        NSDictionary* dictRepresentation = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
        event.state = dictRepresentation[@"state"];
        event.responseStatusCode = (NSInteger)dictRepresentation[@"responseStatusCode"];
        event.error = dictRepresentation[@"error"];
        event.serverResponse = dictRepresentation[@"serverResponse"];
        event.uploadId = dictRepresentation[@"uploadId"];
        NSLog(@"fetched event %@", event.data);
    }
    return events;
}

+ (NSManagedObjectModel *)tableRepresentation{
    NSManagedObjectModel *model = [[NSManagedObjectModel alloc] init];
    NSEntityDescription *entity = [[NSEntityDescription alloc] init];
    [entity setName:@"UploadEvent"];
    [entity setManagedObjectClassName:@"UploadEvent"];
    NSAttributeDescription *fileDataAttribute = [[NSAttributeDescription alloc] init];
    [fileDataAttribute setName:@"data"];
    [fileDataAttribute setAttributeType:NSStringAttributeType];
    [fileDataAttribute setOptional:NO];
    [entity setProperties:@[fileDataAttribute]];
    [model setEntities:@[entity]];
    return model;
}

+(void)setupStorage{
    NSString* path = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES)[0];
    NSLog(@"%@",path);
    NSURL *storeURL = [NSURL fileURLWithPath:[path stringByAppendingPathComponent:@"Background-upload-plugin.db"]];
    NSError *error = nil;
    persistentStoreCoordinator = [[NSPersistentStoreCoordinator alloc] initWithManagedObjectModel: [self tableRepresentation]];
    if (![persistentStoreCoordinator addPersistentStoreWithType:NSSQLiteStoreType configuration:nil URL:storeURL options:nil error:&error]){
        if (error)
            NSLog(@"error setting up core data: %@", error);
    }
    managedObjectContext = [[NSManagedObjectContext alloc] initWithConcurrencyType:NSPrivateQueueConcurrencyType];
    managedObjectContext.mergePolicy = NSMergeByPropertyObjectTrumpMergePolicy;
    [managedObjectContext setPersistentStoreCoordinator:persistentStoreCoordinator];
}
@end
