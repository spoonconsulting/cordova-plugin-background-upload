// Type definitions for Cordova Background Upload Plugin

interface FileUploadOptions {
    
    serverUrl: String;
	filePath: String;
	numberOfRetries: number;
}

interface FileTransferManager {
	upload(payload: FileUploadOptions);
}
