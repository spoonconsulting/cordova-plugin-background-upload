#import <UIKit/UIKit.h>
#import <Cordova/CDVPlugin.h>
#import "FileUploader.h"

@interface FileTransferBackground : CDVPlugin<FileUploaderDelegate>
-(void)startUpload:(CDVInvokedUrlCommand*)command;
-(void)removeUpload:(CDVInvokedUrlCommand*)command;
-(void)initManager:(CDVInvokedUrlCommand*)command;
-(void)acknowledgeEvent:(CDVInvokedUrlCommand*)command;
-(void)destroy:(CDVInvokedUrlCommand*)command;
@end
