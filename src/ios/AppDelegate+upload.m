#import <objc/runtime.h>
#import "AppDelegate+upload.h"
NSString const *callbackKey = @"com.bg.category.block.unique.key";

@implementation AppDelegate (upload)

- (id) getCommandInstance:(NSString*)className{
    return [self.viewController getCommandInstance:className];
}

//Since ivars are not allowed to be synthesized inside categories
//use Associative References to keep the variables

- (void)setBackgroundCompletionBlock:(dispatch_block_t )bl
{
    objc_setAssociatedObject(self, &callbackKey, bl, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (dispatch_block_t )backgroundCompletionBlock
{
    return objc_getAssociatedObject(self, &callbackKey);
}

// its dangerous to override a method from within a category.
// Instead we will use method swizzling. we set this up in the load call.
+ (void)load
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        Class class = [self class];
        
        SEL originalSelector = @selector(init);
        SEL swizzledSelector = @selector(uploadPluginSwizzledInit);
        
        Method original = class_getInstanceMethod(class, originalSelector);
        Method swizzled = class_getInstanceMethod(class, swizzledSelector);
        
        BOOL didAddMethod =
        class_addMethod(class,
                        originalSelector,
                        method_getImplementation(swizzled),
                        method_getTypeEncoding(swizzled));
        
        if (didAddMethod) {
            class_replaceMethod(class,
                                swizzledSelector,
                                method_getImplementation(original),
                                method_getTypeEncoding(original));
        } else {
            method_exchangeImplementations(original, swizzled);
        }
    });
}

- (AppDelegate *)uploadPluginSwizzledInit{
    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self uploadPluginSwizzledInit];
}

- (void)application:(UIApplication *)application handleEventsForBackgroundURLSession:(NSString *)identifier completionHandler:(void (^)())completionHandler{
    // All we do is snarf the completion block so that the -setDidFinishEventsForBackgroundURLSessionBlock:
    // delegate method can call it.
    self.backgroundCompletionBlock = completionHandler;
    NSLog(@"[CD]application handleEventsForBackgroundURLSession: %@",identifier);
    //at this point we know that we are being invoked in background when the daemon has some upload information
    //resume the session to receive progress of the uploads
    //    [[FileUploadManager sharedInstance] start];
}

@end
