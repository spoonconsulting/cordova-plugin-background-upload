const PORT = process.env.PORT || 3000
const express = require('express')
const Busboy = require('busboy')
const fs = require('fs')
const path = require('path')

const handleUpload = (req, res, next) => {
  const busboy = new Busboy({ headers: req.headers })
  let response = {
    originalFilename: null,
    accessMode: 'public',
    height: 4048,
    grayscale: false,
    width: 3036,
    headers: req.headers,
    parameters: {}
  }

  busboy.on('file', function(fieldName, file, fileName) {
    response.originalFilename = fileName
    file.pipe(fs.createWriteStream(path.join('./uploads', fileName)));
  });

  busboy.on('field', function(fieldName, value) {
    response.parameters[fieldName] = value;
  });

  busboy.on('finish', function() {
    res.status(req.method == 'POST' ? 201 : 200).send(JSON.stringify({ receivedInfo: response }))
  });

  return req.pipe(busboy);
}

const app = express()

app.post('/upload', handleUpload)
app.put('/upload', handleUpload)

app.listen(PORT, () => console.log(`Listening on ${PORT}`))
