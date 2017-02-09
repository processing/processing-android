#!/bin/bash

# The addon repository description file:
#
# https://dl.google.com/android/repository/addon.xml
# 
# contains the entry Google Repository (Local Maven repository for Support Libraries)
# which holds the latest file with the wearable pacakges
#
# On an already installed SDK, they should be available in:
#
# $ANDROID_SDK/extras/google/m2repository/com/google/android/support/wearable/$version/

# Usage:
# call with the version number of the wearable package to copy from local SDK and extract, i.e.:
# ./wear-update.sh 2.0.0-beta2:

version=2.0.0-beta2

cp $ANDROID_SDK/extras/google/m2repository/com/google/android/support/wearable/$version/wearable-$version.aar .

# Now unzip, and extract the classes jar. For more information on the aar format and how
# to use it in Eclipse, see this blogposts:
# https://commonsware.com/blog/2014/07/03/consuming-aars-eclipse.html
# and Google's documentation on Android Libraries:
# https://developer.android.com/studio/projects/android-library.html

unzip wearable-$version.aar -d wearable

cp wearable/classes.jar ../core/library/wearable-$version.jar
cp wearable/classes.jar ../mode/wearable-$version.jar

# Remove left over files
rm -Rf wearable
rm wearable-$version.aar

# Done, print out reminder...
echo ""
echo "Done!"
echo "Remember to update the version number of the wearable jar in processing-core's source code"
