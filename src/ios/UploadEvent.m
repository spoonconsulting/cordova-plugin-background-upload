#import "UploadEvent.h"
@interface UploadEvent()
@property (nonatomic, strong) NSString* data;
@end
@implementation UploadEvent
@synthesize data;
static NSManagedObjectContext * managedObjectContext;
static NSPersistentStoreCoordinator * persistentStoreCoordinator;
-(id)init{
    NSEntityDescription *entity = [NSEntityDescription entityForName:@"UploadEvent" inManagedObjectContext:managedObjectContext];
    self = [super initWithEntity:entity insertIntoManagedObjectContext:managedObjectContext];
    if (self == nil)
        return nil;
    return self;
}

-(void)save{
    [managedObjectContext performBlockAndWait:^{
        NSError* error;
        if (![managedObjectContext save:&error])
            NSLog(@"error saving UploadEvent %@ : %@", self, error);
    }];
}

-(void)destroy{
    [managedObjectContext performBlock:^{
        [managedObjectContext deleteObject:self];
        NSError* error;
        if (![managedObjectContext save:&error])
            NSLog(@"error deleting UploadEvent %@ : %@", self, error);
    }];
}

-(NSDictionary*)dataRepresentation{
    NSData *data = [self.data dataUsingEncoding:NSUTF8StringEncoding];
    NSMutableDictionary* dictRepresentation = [[NSJSONSerialization JSONObjectWithData:data options:0 error:nil] mutableCopy];
    [dictRepresentation addEntriesFromDictionary: @{
        @"eventId" : self.objectID.URIRepresentation.absoluteString
    }];
    return dictRepresentation;
}

+(UploadEvent*)eventWithId:(NSString*)eventId{
    NSManagedObjectID* objectId = [persistentStoreCoordinator managedObjectIDForURIRepresentation: [NSURL URLWithString:eventId]];
    return objectId ? [managedObjectContext objectWithID:objectId] : nil;
}

+(NSArray*)allEvents{
    NSFetchRequest* request = [NSFetchRequest fetchRequestWithEntityName:@"UploadEvent"];
    request.returnsObjectsAsFaults = NO;
    return [managedObjectContext executeFetchRequest:request error:NULL];
}

+(UploadEvent*)create:(NSDictionary*)info{
    UploadEvent* event = [[UploadEvent alloc] init];
    NSData * jsonData = [NSJSONSerialization dataWithJSONObject:info options:0 error:nil];
    event.data = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    [event save];
    return event;
}

+(void)setupStorage{
    NSString* path = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES)[0];
    NSURL *storeURL = [NSURL fileURLWithPath:[path stringByAppendingPathComponent:@"Background-upload-plugin.db"]];
    NSError *error = nil;
    persistentStoreCoordinator = [[NSPersistentStoreCoordinator alloc] initWithManagedObjectModel: [self tableRepresentation]];
    if (![persistentStoreCoordinator addPersistentStoreWithType:NSSQLiteStoreType configuration:nil URL:storeURL options:nil error:&error])
        NSLog(@"error setting up core data: %@", error);
    managedObjectContext = [[NSManagedObjectContext alloc] initWithConcurrencyType:NSPrivateQueueConcurrencyType];
    managedObjectContext.mergePolicy = NSMergeByPropertyObjectTrumpMergePolicy;
    [managedObjectContext setPersistentStoreCoordinator:persistentStoreCoordinator];
}

+(NSManagedObjectModel*)tableRepresentation{
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

@end
