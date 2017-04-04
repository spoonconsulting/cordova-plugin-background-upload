var webpack = require('webpack');
module.exports = {
  entry: {
    "backgroundupload": "./src/backgroundupload.coffee",
    "backgroundupload.min": "./src/backgroundupload.coffee",
  },
  output: {
    path: __dirname,
    filename: "dist/[name].js",
    libraryTarget: "umd",
    library: '[name]'
  },
  module: {
    loaders: [
      { test: /\.coffee$/, loaders: ["coffee-loader" ] }
    ]
  },
  plugins: [
    new webpack.optimize.UglifyJsPlugin({ include: /\.min\.js$/ }),
  ]
};
