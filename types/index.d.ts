// Type definitions for Cordova Background Upload Plugin

interface FileUploadOptions {
    
    serverUrl: String;
	filePath: String;
	numberOfRetries?: number;
	headers?: any;
	parameters?: any;
}

interface FileTransferManager {

	upload(payload: FileUploadOptions);
}


declare var FileTransferManager: {
	new (): FileTransferManager;
    (): any;
};
declare var FileUploadOptions: FileUploadOptions;