ver=$1

mvn install:install-file -DgroupId=org.processing.android -DartifactId=processing-core -Dversion=$ver -Dpackaging=jar -DgeneratePom=true -DlocalRepositoryPath=. -DcreateChecksum=true -Dfile=../pkgs/processing-core-$ver.jar
