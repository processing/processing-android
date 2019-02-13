![Image](imgs/bg_1.png)

# AR library for Processing-Android

![Android](https://img.shields.io/badge/platform-Android-green.svg?longCache=true&style=for-the-badge)   ![ARCore](https://img.shields.io/badge/ARCore-v1.2.0-blue.svg?longCache=true&style=for-the-badge)   ![In Progress](https://img.shields.io/badge/in--progress-true-green.svg?longCache=true&style=for-the-badge) <br />
This library includes ARCore renderer to create AR apps using Processing.

## Steps to build:
* Make sure you have both [Processing](https://github.com/processing/processing) and [Processing-Android](https://github.com/processing/processing-android) built before you proceed. <br />
* For Building [Processing-Android](https://github.com/processing/processing-android) refer [Wiki](https://github.com/processing/processing-android/wiki/Building-Processing-for-Android). <br />
* Once built, clone [processing-ar](https://github.com/SyamSundarKirubakaran/processing-ar) into the `Libraries` Directory right next to `vr` Directory. <br />
<b>NOTE:</b> Rename the cloned directory as `ar` and the name of the module to be `processing-ar`. <br />
* Make sure to import `processing-ar` as a module in your IDE. <br />
* Build it using the `ant` command through terminal and on Successful build, you'll see `ar.jar` file appear under `libraries/ar/library`. <br />
* Once built, hit Run. You'll see `AR` appear under `Sketch -> Import Library... -> AR`. <br />
* On clicking it, you'll get an import to the <b>AR Library</b> as `import processing.ar.*;`. <br />

## Working:
<p align="center">
  <img src="imgs/init_1.gif">
</p>