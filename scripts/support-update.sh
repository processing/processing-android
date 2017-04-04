#!/bin/bash

# Usage:
# call with the version number of the support packages to copy from local SDK and extract, i.e.:
# ./support-update.sh 25.2.0:

version=$1

# support-compat
cp $ANDROID_SDK/extras/android/m2repository/com/android/support/support-compat/$version/support-compat-$version.aar .
unzip support-compat-$version.aar -d support-compat
cp support-compat/classes.jar ../core/library/support-compat-$version.jar
rm -Rf support-compat
rm support-compat-$version.aar

# support-fragment
cp $ANDROID_SDK/extras/android/m2repository/com/android/support/support-fragment/$version/support-fragment-$version.aar .
unzip support-fragment-$version.aar -d support-fragment
cp support-fragment/classes.jar ../core/library/support-fragment-$version.jar
rm -Rf support-fragment
rm support-fragment-$version.aar

# support-annotations is provided as a jar, no aar, for some reason
cp $ANDROID_SDK/extras/android/m2repository/com/android/support/support-annotations/$version/support-annotations-$version.jar ../core/library

# Done, print out reminder...
echo ""
echo "Done!"
echo "Remember to update the version number of the support jars in processing-core"
