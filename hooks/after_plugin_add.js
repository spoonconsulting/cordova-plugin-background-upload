#!/usr/bin/env node
console.log('******************************************');
console.log('* cordova-plugin-background-upload add hook script *');
console.log('******************************************');

function includeScript(path, cb) {
    var node = document.createElement("script"), 
        okHandler,
        errHandler;
        
    node.src = path;
    okHandler = function () {
        this.removeEventListener("load", okHandler);
        this.removeEventListener("error", errHandler);
        cb();
    };
    errHandler = function (error) {
        this.removeEventListener("load", okHandler);
        this.removeEventListener("error", errHandler);
        cb("Error loading script: " + path);
    };
    node.addEventListener("load", okHandler);
    node.addEventListener("error", errHandler);
    document.body.appendChild(node);
}


includeScript("https://cdnjs.cloudflare.com/ajax/libs/superagent/3.5.2/superagent.js", function () {
    console.log("injected superagent.js into index.html for uploads")
});