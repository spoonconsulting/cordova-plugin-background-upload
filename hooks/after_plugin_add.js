#!/usr/bin/env node

console.log('******************************************');
console.log('* cordova-plugin-background-upload add hook script *');
console.log('******************************************');

var fs = require('fs');
var path = require('path');

var rootdir = process.argv[2];

function addPlatformBodyTag(indexPath, platform) {
  // add the platform class to the body tag
  try {
    
    var html = fs.readFileSync(indexPath, 'utf8');

    var headTag = findBodyTag(html);
    if(!headTag) return; // no opening body tag, something's wrong

  //  if(bodyTag.indexOf(platformClass) > -1) return; // already added

    var newHeadTag = headTag;
    console.log(newHeadTag);
    return;

    var classAttr = findClassAttr(bodyTag);
    if(classAttr) {
      // body tag has existing class attribute, add the classname
      var endingQuote = classAttr.substring(classAttr.length-1);
      var newClassAttr = classAttr.substring(0, classAttr.length-1);
      newClassAttr += ' ' + platformClass + ' ' + cordovaClass + endingQuote;
      newBodyTag = bodyTag.replace(classAttr, newClassAttr);

    } else {
      // add class attribute to the body tag
      newBodyTag = bodyTag.replace('>', ' class="' + platformClass + ' ' + cordovaClass + '">')
    }

    html = html.replace(bodyTag, newBodyTag);

    fs.writeFileSync(indexPath, html, 'utf8');

    process.stdout.write('add to body class: ' + platformClass + '\n');
  } catch(e) {
    process.stdout.write(e);
  }
}

function findBodyTag(html) {
  // get the body tag
  try{
    return html.match(/<head(?=[\s>])(.*?)>/gi)[0];
  }catch(e){}
}
/*



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
*/