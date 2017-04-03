// Type definitions for Cordova Background Upload Plugin
export interface FileUploadOptions {
    
    serverUrl: String;
	filePath?: String;
	file?: File; 
	headers?: any;
	parameters?: any;
}
export interface FileTransferManager {

	upload(payload: FileUploadOptions);
}

declare var FileTransferManager: {
	new (): FileTransferManager;
    (): FileTransferManager;
};