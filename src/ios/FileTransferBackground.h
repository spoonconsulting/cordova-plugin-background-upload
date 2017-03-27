#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>

// TODO This means that you can start a download of a large image or file, close the app and
// the download wilcontinue until it completes.
@interface FileTransferBackground : CDVPlugin <NSURLSessionDelegate, NSURLSessionTaskDelegate>

@property NSString *callbackId;
@property (weak, nonatomic) IBOutlet UIButton *btnStartUpload;


@property (nonatomic) NSDictionary *uploadSettings;
@property (nonatomic, weak) NSURLSession *session;

- (void)startUpload:(CDVInvokedUrlCommand*)command;

@end