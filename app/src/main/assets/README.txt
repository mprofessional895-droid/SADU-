Drop the home-screen artwork here as exactly: hero.png

Any PNG/JPG the Android BitmapFactory reads works. Use a portrait image with the
subject high and empty dark space at the bottom, because the app crops to fill the
screen and draws the greeting over the lower part.

The file is loaded at runtime, so replacing it does not change any code - just
rebuild the APK.
