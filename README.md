# Processing Android Maven Repository

This is the Maven repository with the processing-core library.

To import into your project add https://raw.github.com/processing/processing-android/repository/ as a maven repository in the ```settings.gradle``` file of the project, under the  dependencyResolutionManagement block:

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
    implementation 'org.processing.android:processing-core:4.6.0'
}
```

More information below about declaring respositories in Gradle:

https://docs.gradle.org/current/userguide/declaring_repositories.html