#!/bin/bash

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
