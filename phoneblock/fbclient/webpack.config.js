module.exports = {
  entry: './src/upload-blocked-calls.mjs',
  
  resolve: {
    fallback: { 
    	"stream": require.resolve("stream-browserify"),
    	"crypto": require.resolve("crypto-browserify"),
    	"https": require.resolve("https-browserify"),
    	"http": require.resolve("stream-http"),
    	"path": require.resolve("path-browserify"),
    	"zlib": require.resolve("browserify-zlib"),
    	"timers": require.resolve("timers-browserify"),
    	"assert": require.resolve("assert-browserify"),
    }
  },
  
  node: {
    __dirname: false,
  },
  
  experiments: {
  	topLevelAwait: true
  },
  
  module: {
    rules: [
      {
        test: /\.node$/,
        loader: "node-loader",
      },
    ],
  },
  
};
