const PORT = process.env.PORT || 3000;
var express = require('express'),
  multer = require('multer'),
  storage = multer.diskStorage({
    destination: function (req, file, next) {
      next(null, './uploads')
    },
    filename: function (req, file, next) {
      next(null, file.originalname);
    }
  }),
  upload = multer({
    storage: storage
  }),
  fUpload = upload.fields([{
    name: 'file',
    maxCount: 1
  }]),
  app = express();

app.get('/', (req, res) => {
  res.send("Welcome to test server");
})

app.post('/upload', fUpload, (req, res, next) => {
  // Field data
  // console.log(req.headers);
  const fileName = req.files.file[0].originalname;
  console.log(fileName);
  fUpload(req, res, (err) => {
    if (err) {
      console.log("An error occurred when uploading");
    } else {
      var response = {
        "original_filename": fileName,
        "access_mode": "public",
        "height": 4032,
        "grayscale": false,
        "width": 3024,
        "image_metadata": {
          "GPSLatitude": "33 deg 6' 2.29\" N",
          "GPSTimeStamp": "18:52:19",
          "ShutterSpeedValue": "1/120",
        },
        "created_at": "2019-09-10T03:24:25Z"
      };
      res.send(JSON.stringify(response));
    }
  });
});

app.listen(PORT, () => console.log(`Listening on ${ PORT }`));