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
mkdir vr_base

# Start by copying manifest and resources
cp base.aar/AndroidManifest.xml vr_base
cp -R base.aar/res/ vr_base/res

# Extract classes from library's jar
unzip base.aar/classes.jar -d base.aar/classes
mkdir vr_base/libs
mkdir vr_base/libs/base
# Copy classes
cp -R base.aar/classes/com vr_base/libs/base/com
# Copy native libs
cp -R base.aar/jni vr_base/libs/base/lib
# Create the jar file
jar cf vr_base-classes.jar -C vr_base/libs/base .

# Remove original folder and put jar file in its final location
rm -Rf vr_base/libs/base
mv vr_base-classes.jar vr_base/libs

# Need the jar also in cardboard's lib folder
cp vr_base/libs/vr_base-classes.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r vr_base.zip vr_base
mv vr_base.zip ../libraries/cardboard/gvrsdk/$ver


################################################
# Common package
mkdir vr_common

# Start by copying manifest and resources
cp common.aar/AndroidManifest.xml vr_common
cp -R common.aar/res/ vr_common/res

# Extract classes from library's jar
unzip common.aar/classes.jar -d common.aar/classes
mkdir vr_common/libs
mkdir vr_common/libs/common
# Copy classes
cp -R common.aar/classes/com vr_common/libs/common/com
# Create the jar file
jar cf vr_common-classes.jar -C vr_common/libs/common .

# Remove original folder and put jar file in its final location
rm -Rf vr_common/libs/common
mv vr_common-classes.jar vr_common/libs

# protobuf-javanano was unbundled from the SDK:
# https://github.com/googlevr/gvr-android-sdk/issues/264
# but required by vr_common, the correspond pom file alongside the aar contains the 
# details of the dependency.
wget http://central.maven.org/maven2/com/google/protobuf/nano/protobuf-javanano/$nano/protobuf-javanano-$nano.jar
mv protobuf-javanano-$nano.jar vr_common/libs

# Need the jar also in cardboard's lib folder
cp vr_common/libs/vr_common-classes.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r vr_common.zip vr_common
mv vr_common.zip ../libraries/cardboard/gvrsdk/$ver

################################################
# Audio package
mkdir vr_audio

# Start by copying manifest and resources
cp audio.aar/AndroidManifest.xml vr_audio
cp -R audio.aar/res/ vr_audio/res

# Extract classes from library's jar
unzip audio.aar/classes.jar -d audio.aar/classes
mkdir vr_audio/libs
mkdir vr_audio/libs/audio
# Copy classes
cp -R audio.aar/classes/com vr_audio/libs/audio/com
# Copy native libs
cp -R audio.aar/jni vr_audio/libs/audio/lib
# Create the jar file
jar cf vr_audio-classes.jar -C vr_audio/libs/audio .

# Remove original folder and put jar file in its final location
rm -Rf vr_audio/libs/audio
mv vr_audio-classes.jar vr_audio/libs

# Need the jar also in cardboard's lib folder
cp vr_audio/libs/vr_audio-classes.jar ../libraries/cardboard/lib

# Finally, create zip file and mode to the sdk location
zip -r vr_audio.zip vr_audio
mv vr_audio.zip ../libraries/cardboard/gvrsdk/$ver

################################################
# Cleanup
rm -Rf vr_base
rm -Rf base.aar

rm -Rf vr_common
rm -Rf common.aar

rm -Rf vr_audio
rm -Rf audio.aar

rm -Rf gvr-android-sdk-$ver
rm $sdk.zip

# Done, print out reminder...
echo ""
echo "Done!"
echo "Remember to update the GVR path jar in the mode's source code"