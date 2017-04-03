#!/usr/bin/env node

console.log('******************************************');
console.log('* cordova-plugin-background-upload add hook script *');
console.log('******************************************');

var fs = require('fs');
var path = require('path');

try {
  console.log("Running hook: " + path.resolve());
  console.log(path.resolve(__dirname, "../../../", "platforms/browser/index.html"));

  var indexPath = path.resolve(__dirname, "../../../", "platforms/browser/index.html");

  if (!fs.existsSync(indexPath)) {
    return
  }
  var html = fs.readFileSync(indexPath, 'utf8');
  if (html.indexOf("superagent.js") != -1) {
    //it aleady contains the script
    return;
  }
  const cheerio = require('cheerio');
  const $ = cheerio.load(html);
  $('head').prepend('<script src="https://cdnjs.cloudflare.com/ajax/libs/superagent/3.5.2/superagent.js"></script>');
  fs.writeFileSync(indexPath, $.html(), 'utf8');
  console.log('injected superagent.js into index.html for browser');
 
} catch (e) {
  console.log(e);
}
