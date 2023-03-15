# Processing Android Maven Repository

This is a Maven repository that can be accessed publicly from Gradle, etc.

Made following the instructions in this gist:

https://gist.github.com/fernandezpablo85/03cf8b0cd2e7d8527063

One caveat, use mvn version 3.8.7 because of this [bug](https://issues.apache.org/jira/browse/MNG-7679) or provide a pom.xml file instead having mvn auto-generating it.

Place the processing-core-x.y.z.jar from building the core library into ../pkgs and then run:

```
./release.sh x.y.z
```

## Importing into Gradle projects

In the settings.gradle file of the project, add https://raw.github.com/processing/processing-android/repository/ as a maven repository, under the  dependencyResolutionManagement block:

```
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url "https://raw.github.com/processing/processing-android/repository/"
        }
    }
}
```

Then you should able to incorporate processing-core as a dependency in the build.gradle file:

```
dependencies {
    implementation 'androidx.appcompat:appcompat:1.4.1'
    implementation 'com.google.android.material:material:1.5.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.3'
    implementation 'androidx.navigation:navigation-fragment:2.4.1'
    implementation 'androidx.navigation:navigation-ui:2.4.1'

    implementation 'org.processing.android:processing-core:4.5.0b5'
}
```

More information below about declaring respositories in Gradle:

https://docs.gradle.org/current/userguide/declaring_repositories.html