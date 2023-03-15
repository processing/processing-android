# Processing Android Maven Repository

This is a Maven repository that can be accessed publicly from Gradle, etc.

Made following the instructions in this gist:

https://gist.github.com/fernandezpablo85/03cf8b0cd2e7d8527063

One caveat, use mvn version 3.8.7 because of this [bug](https://issues.apache.org/jira/browse/MNG-7679) or provide a pom.xml file instead having mvn auto-generating it.

Place the processing-core-x.y.z.jar from building the core library into ../pkgs and then run:

```
./release.sh x.y.z
```