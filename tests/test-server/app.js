const PORT = process.env.PORT || 3000
var express = require('express')
var multer = require('multer')
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
          height: 4032,
          grayscale: false,
          width: 3024,
          headers: req.headers,
          parameters: params
        }
      }
      res.status(210).send(JSON.stringify(toSend))
    }
  })
})

app.listen(PORT, () => console.log(`Listening on ${PORT}`))
