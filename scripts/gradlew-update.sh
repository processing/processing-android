# Needs gradle to be installed:
# https://docs.gradle.org/current/userguide/installation.html

# Usage:
# call with the version number of the gradle version to be used by gradlew, i.e.:
# ./gradlew-update.sh 3.2.1:

version=$1

mkdir gradlew
cd gradlew
# Create a wrapper install as described here:
# https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:wrapper_generation
gradle wrapper --gradle-version $version
cd ..

# Package and move to the mode folder
zip -r gradlew.zip gradlew -x *.gradle*
mv gradlew.zip ../mode

# Remove install
rm -Rf gradlew



