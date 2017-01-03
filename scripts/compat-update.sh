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
# call with location of the Android SDk, e.g.:
# ./compat-update.sh ~/code/android/sdk

sdk=$1

pushd $sdk/extras/android/support/v7

# Exclude the project.properties files because Processing will regenerate it when
# building the project
zip -r appcompat.zip appcompat --exclude *project.properties*

popd

mv $sdk/extras/android/support/v7/appcompat.zip ../mode

# Done
echo ""
echo "Done!"
