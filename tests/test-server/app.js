const PORT = process.env.PORT || 3000
var express = require('express')
var multer = require('multer')
var fs = require('fs')
var path = require('path')
var storage = multer.diskStorage({
  destination: function (req, file, next) {
    next(null, './uploads')
  },
  filename: function (req, file, next) {
    next(null, file.originalname)
  }
})
var upload = multer({
  storage: storage
})
var fUpload = upload.fields([{
  name: 'file',
  maxCount: 1
}])
var app = express()

app.get('/', (req, res) => {
  res.send('Welcome to test server')
})

app.post('/upload', fUpload, (req, res, next) => {
  const params = req.body
  const fileName = req.files.file[0].originalname
  fUpload(req, res, (err) => {
    if (err) {
      console.log('An error occurred when uploading')
    } else {
      var toSend = {
        receivedInfo: {
          originalFilename: fileName,
          accessMode: 'public',
          height: 1067,
          grayscale: false,
          width: 800,
          headers: req.headers,
          parameters: params
        }
      }
      res.status(210).send(JSON.stringify(toSend))
    }
  })
})


app.put('/upload', (req, res, next) => {
  req.pipe(fs.createWriteStream(path.join('./uploads', 'tree.jpg'))).on('finish', () => {
    res.status(200).send(JSON.stringify({ success: true }))
  })
})

app.listen(PORT, () => console.log(`Listening on ${PORT}`))
