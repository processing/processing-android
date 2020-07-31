include (":core", ":mode:libraries:vr", ":mode:libraries:ar", "mode:tools:SDKUpdater", ":mode")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
    }
}

