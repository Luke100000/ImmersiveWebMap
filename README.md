# Immersive Web Map

* The API creates a new random instance and returns an access token
* The server remembers that access token and uses it to push its stuff
* The client uses the public instance name, and an optional password to connect to the server

* The API offers endpoints for
  * Storing a chunk (x, y, z, dimension)
    * y is unused but would be used for eventual target depth. We use -1000 if the flat map should be used
  * Storing server meta information
    * Time, weather, etc.
  * Storing dimension meta information
  * Storing chunk meta information
    * This includes banners, signs, ftb chunks, etc.
  * Storing player trajectories
    * Sampled at n block precision with time stamps and movement type
  * Retrieving a chunk (x, y, z, dimension, width, height, downscale)
    * If the chunk is not available, the API returns an empty image
    * Width and height are in chunks and is used to merge multiple chunks into one image
    * Downscale is a factor to downscale the image, using modus
      * To retain the dither, the modus switches its order every second pixel
  * Retrieve a chunks/server/dimension meta information
  * Let's provide a common endpoint instead with flags to control
    * Base (Color, height, biome, blank, etc.)
    * Heatmap (rendered as overlay)
    * Streets (rendered as overlay)
      * Clustering is slow, thus it is heavily cached, tiled and provided at different resolutions at the same format as chunks
    * Claimed regions (rendered as overlay)
    * Or let's not just merge everything, do this on the respective client