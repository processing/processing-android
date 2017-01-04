#!/bin/bash

# This script downloads the GVR SDK, explodes the AAR files, and creates zip packages than
# can be used by the mode to generate the required libraries to build a cardboard sketch.
# The steps in the AAR to ZIP conversion were based on this blogpost:
# https://commonsware.com/blog/2014/07/03/consuming-aars-eclipse.html

# Usage:
# call with the version number of the GVR SDK package to download and extract, i.e.:
# ./gvrsdk-update.sh 1.10.0

ver=$1
sdk=v$1

# Version of the protobuf-javanano dependency
nano=3.1.0

mkdir ../libraries/cardboard/gvrsdk/$ver

wget https://github.com/googlevr/gvr-android-sdk/archive/$sdk.zip

unzip $sdk.zip

#Explode the aars and create the corresponding zip packages to use in the mode
unzip gvr-android-sdk-$ver/libraries/sdk-base-$ver.aar -d base.aar
unzip gvr-android-sdk-$ver/libraries/sdk-common-$ver.aar -d common.aar
unzip gvr-android-sdk-$ver/libraries/sdk-audio-$ver.aar -d audio.aar

################################################
# Base package
mkdir sdk-base

# Start by copying manifest and resources
cp base.aar/AndroidManifest.xml sdk-base
cp -R base.aar/res/ sdk-base/res

# Extract classes from library's jar
unzip base.aar/classes.jar -d base.aar/classes
mkdir sdk-base/libs
mkdir sdk-base/libs/base
# Copy classes
cp -R base.aar/classes/com sdk-base/libs/base/com
# Copy native libs
cp -R base.aar/jni sdk-base/libs/base/lib
# Create the jar file
jar cf gvr-base.jar -C sdk-base/libs/base .

# Remove original folder and put jar file in its final location
rm -Rf sdk-base/libs/base
mv gvr-base.jar sdk-base/libs

# Need the jar also in cardboard's lib folder
cp sdk-base/libs/gvr-base.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r sdk-base.zip sdk-base
mv sdk-base.zip ../libraries/cardboard/gvrsdk/$ver


################################################
# Common package
mkdir sdk-common

# Start by copying manifest and resources
cp common.aar/AndroidManifest.xml sdk-common
cp -R common.aar/res/ sdk-common/res

# Extract classes from library's jar
unzip common.aar/classes.jar -d common.aar/classes
mkdir sdk-common/libs
mkdir sdk-common/libs/common
# Copy classes
cp -R common.aar/classes/com sdk-common/libs/common/com
# Create the jar file
jar cf gvr-common.jar -C sdk-common/libs/common .

# Remove original folder and put jar file in its final location
rm -Rf sdk-common/libs/common
mv gvr-common.jar sdk-common/libs

# protobuf-javanano was unbundled from the SDK:
# https://github.com/googlevr/gvr-android-sdk/issues/264
# but required by sdk-common, the correspond pom file alongside the aar contains the 
# details of the dependency.
wget http://central.maven.org/maven2/com/google/protobuf/nano/protobuf-javanano/$nano/protobuf-javanano-$nano.jar
mv protobuf-javanano-$nano.jar sdk-common/libs

# Need the jar also in cardboard's lib folder
cp sdk-common/libs/gvr-common.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r sdk-common.zip sdk-common
mv sdk-common.zip ../libraries/cardboard/gvrsdk/$ver

################################################
# Audio package
mkdir sdk-audio

# Start by copying manifest and resources
cp audio.aar/AndroidManifest.xml sdk-audio
cp -R audio.aar/res/ sdk-audio/res

# Extract classes from library's jar
unzip audio.aar/classes.jar -d audio.aar/classes
mkdir sdk-audio/libs
mkdir sdk-audio/libs/audio
# Copy classes
cp -R audio.aar/classes/com sdk-audio/libs/audio/com
# Copy native libs
cp -R audio.aar/jni sdk-audio/libs/audio/lib
# Create the jar file
jar cf gvr-audio.jar -C sdk-audio/libs/audio .

# Remove original folder and put jar file in its final location
rm -Rf sdk-audio/libs/audio
mv gvr-audio.jar sdk-audio/libs

# Need the jar also in cardboard's lib folder
cp sdk-audio/libs/gvr-audio.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r sdk-audio.zip sdk-audio
mv sdk-audio.zip ../libraries/cardboard/gvrsdk/$ver

################################################
# Cleanup
rm -Rf sdk-base
rm -Rf base.aar

rm -Rf sdk-common
rm -Rf common.aar

rm -Rf sdk-audio
rm -Rf audio.aar

rm -Rf gvr-android-sdk-$ver
rm $sdk.zip

# Done, print out reminder...
echo ""
echo "Done!"
echo "Remember to update the GVR path jar in the mode's source code"