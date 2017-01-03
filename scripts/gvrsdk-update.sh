#!/bin/bash

# This script downloads the GVR SDK, explodes the AAR files, and creates zip packages than
# can be used by the mode to generate the required libraries to build a cardboard sketch.
# The steps in the AAR to ZIP conversion were based on this blogpost:
# https://commonsware.com/blog/2014/07/03/consuming-aars-eclipse.html

# Usage:
# call with the version number of the GVR SDK package to download and extract, i.e.:
# ./gvrsdk-update.sh 1.0.1

ver=$1
sdk=v$1

mkdir ../libraries/cardboard/gvrsdk/$ver

wget https://github.com/googlevr/gvr-android-sdk/archive/$sdk.zip

unzip $sdk.zip

#Explode the aars and create the corresponding zip packages to use in the mode
unzip gvr-android-sdk-$ver/libraries/base/base.aar -d base.aar
unzip gvr-android-sdk-$ver/libraries/common/common.aar -d common.aar
unzip gvr-android-sdk-$ver/libraries/audio/audio.aar -d audio.aar

################################################
# Base package
mkdir base

# Start by copying manifest and resources
cp base.aar/AndroidManifest.xml base
cp -R base.aar/res/ base/res

# Extract classes from library's jar
unzip base.aar/classes.jar -d base.aar/classes
mkdir base/libs
mkdir base/libs/base
# Copy classes
cp -R base.aar/classes/com base/libs/base/com
# Copy native libs
cp -R base.aar/jni base/libs/base/lib
# Create the jar file
jar cf base.jar -C base/libs/base .

# Remove original folder and put jar file in its final location
rm -Rf base/libs/base
mv base.jar base/libs

# Need the jar also in cardboard's lib folder
cp base/libs/base.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r base.zip base
mv base.zip ../libraries/cardboard/gvrsdk/$ver


################################################
# Common package
mkdir common

# Start by copying manifest and resources
cp common.aar/AndroidManifest.xml common
cp -R common.aar/res/ common/res

# Extract classes from library's jar
unzip common.aar/classes.jar -d common.aar/classes
mkdir common/libs
mkdir common/libs/common
# Copy classes
cp -R common.aar/classes/com common/libs/common/com
# Create the jar file
jar cf common.jar -C common/libs/common .

# Remove original folder and put jar file in its final location
rm -Rf common/libs/common
mv common.jar common/libs

# Need the jar also in cardboard's lib folder
cp common/libs/common.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r common.zip common
mv common.zip ../libraries/cardboard/gvrsdk/$ver

################################################
# Audio package
mkdir audio

# Start by copying manifest and resources
cp audio.aar/AndroidManifest.xml audio
cp -R audio.aar/res/ audio/res

# Extract classes from library's jar
unzip audio.aar/classes.jar -d audio.aar/classes
mkdir audio/libs
mkdir audio/libs/audio
# Copy classes
cp -R audio.aar/classes/com audio/libs/audio/com
# Copy native libs
cp -R audio.aar/jni audio/libs/audio/lib
# Create the jar file
jar cf audio.jar -C audio/libs/audio .

# Remove original folder and put jar file in its final location
rm -Rf audio/libs/audio
mv audio.jar audio/libs

# Need the jar also in cardboard's lib folder
cp audio/libs/audio.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r audio.zip audio
mv audio.zip ../libraries/cardboard/gvrsdk/$ver

################################################
# Cleanup
rm -Rf base
rm -Rf base.aar

rm -Rf common
rm -Rf common.aar

rm -Rf audio
rm -Rf audio.aar

rm -Rf gvr-android-sdk-$ver
rm $sdk.zip

# Done, print out reminder...
echo ""
echo "Done!"
echo "Remember to update the GVR path jar in the mode's source code"