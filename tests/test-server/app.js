const PORT = process.env.PORT || 3000
const express = require('express')
const Busboy = require('busboy')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

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

const echoedHeaders = ['content-length', 'transfer-encoding', 'content-md5', 'authorization', 'x-ms-meta-foo']

const handleRawUpload = (req, res) => {
  const chunks = []
  req.on('data', (chunk) => chunks.push(chunk))
  req.on('end', () => {
    const body = Buffer.concat(chunks)
    fs.mkdirSync('./uploads', { recursive: true })
    fs.writeFileSync(path.join('./uploads', `raw-upload-${Date.now()}`), body)
    echoedHeaders.forEach((name) => {
      if (req.headers[name] !== undefined) res.set(`x-echo-${name}`, req.headers[name])
    })
    res.set('x-echo-original-url', req.originalUrl)
    res.set('x-echo-body-md5', crypto.createHash('md5').update(body).digest('hex'))
    res.set('x-echo-body-length', String(body.length))
    res.status(201).end()
  })
}

const app = express()

app.post('/upload', handleUpload)
app.put('/upload', handleUpload)
app.put('/raw-upload', handleRawUpload)

app.listen(PORT, () => console.log(`Listening on ${PORT}`))
