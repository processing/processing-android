#!/bin/bash

# I have being unable to find Google's repository from where Android Studio gets the wear
# aar files. People have been trying to solve this in relation to using Eclipse to build
# wear apps, for example:
# http://stackoverflow.com/questions/27913902/dependencies-from-android-gradle-build-or-how-to-build-an-android-wear-app-wit
# However, both JCenter and Maven Central don't seem to hold any wearable pacakges:
# https://jcenter.bintray.com/com/google/android/
# https://search.maven.org/#search%7Cga%7C1%7Cwearable
# So far, only found two odd repostories with up-to-date versions of the aar packages:
# http://uiq3.sourceforge.net/Repository/com/google/android/support/wearable/
# http://mvn.sibext.com/com/google/android/support/wearable/
# (one is maintained by a company in Siberia)

# Usage:
# call with the version number of the wearable package to download and extract, i.e.:
# ./wear-update.sh 1.4.0

version=$1

# Download the requested aar package from the sourceforge repository
wget http://uiq3.sourceforge.net/Repository/com/google/android/support/wearable/$version/wearable-$version.aar

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