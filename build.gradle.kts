import java.nio.file.Files
import org.zeroturnaround.zip.ZipUtil
import org.apache.commons.io.FileUtils
import java.io.*
import java.lang.*
import java.util.regex.Pattern
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

//apply plugin: "java"
//apply plugin: "kotlin"

plugins {
    `kotlin-dsl`
    java
    kotlin("jvm") version("1.3.72")
}

buildscript {
    extra["kotlin_version"] = "1.3.72"
    extra["dokka_version"] = "0.10.1"

    repositories {
        google()
        jcenter()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
    }

    dependencies {
        classpath ("com.android.tools.build:gradle:3.5.1")
        classpath ("commons-io:commons-io:2.6")
        classpath ("org.zeroturnaround:zt-zip:1.13")
        classpath ("org.jetbrains.kotlin:kotlin-gradle-plugin:${project.extra["kotlin_version"]}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${project.extra["dokka_version"]}")

    }
}

allprojects {
    apply (plugin ="java")
    apply (plugin ="java-library")
    apply (plugin ="org.jetbrains.dokka")

    extra["kotlin_version"] = "1.3.72"
    extra["dokka_version"]  = "0.10.1"

    // issue in kotlin dsl - https://discuss.gradle.org/t/groovy-to-kotlin-dsl-properties/31022
    val versionPath = FileInputStream(project.rootProject.file("mode/version.properties"))

    val versions = org.jetbrains.kotlin.konan.properties.Properties()
    versions.load(versionPath)

    extra["targetSdkVersion"] = versions.getProperty("android-platform")
    extra["supportLibsVersion"] = versions.getProperty("com.android.support%support-v4")
    extra["wearVersion"] = versions.getProperty("com.google.android.support%wearable")
    extra["gvrVersion"] = versions.getProperty("com.google.vr")
    extra["garVersion"] = versions.getProperty("com.google.ar")
    extra["processingVersion"] = versions.getProperty("org.processing")
    extra["toolingVersion"] = versions.getProperty("org.gradle%gradle-tooling-api")
    extra["slf4jVersion"] = versions.getProperty("org.slf4j")
    extra["gradlewVersion"] = versions.getProperty("gradlew")
    extra["toolsLibVersion"] = versions.getProperty("android-toolslib")
    extra["jdtVersion"] = versions.getProperty("org.eclipse.jdt")


    // issue in kotlin dsl - https://discuss.gradle.org/t/groovy-to-kotlin-dsl-properties/31022
    val modePropertyPath = FileInputStream(project.rootProject.file("mode/mode.properties"))
    val modeProperties = org.jetbrains.kotlin.konan.properties.Properties()
    modeProperties.load(modePropertyPath)
    extra["modeVersion"] = modeProperties.getProperty("prettyVersion")

    // issue in kotlin dsl - https://discuss.gradle.org/t/groovy-to-kotlin-dsl-properties/31022
    val vrPropertiesPath = FileInputStream(project.rootProject.file("mode/libraries/vr/library.properties"))
    val vrProperties = org.jetbrains.kotlin.konan.properties.Properties()
    vrProperties.load(vrPropertiesPath)
    extra["vrLibVersion"] = vrProperties.getProperty("prettyVersion")

    // issue in kotlin dsl - https://discuss.gradle.org/t/groovy-to-kotlin-dsl-properties/31022
    val arPropertiesPath = FileInputStream(project.rootProject.file("mode/libraries/ar/library.properties"))
    val arProperties = org.jetbrains.kotlin.konan.properties.Properties()
    arProperties.load(arPropertiesPath)
    extra["arLibVersion"] = arProperties.getProperty("prettyVersion")


    val fn = project.rootProject.file("local.properties")
    if (!fn.exists()) {
        if (System.getenv("ANDROID_SDK") != null) {
            val syspath = System.getenv("ANDROID_SDK")
            val parts = syspath.split(Pattern.quote(File.separator))
            val path = StringBuilder()

            for(i in 0 until parts.size) {
                path.append(parts.get(i))
                if (i < parts.size - 1)
                    path.append("/")
            }

            val fnwriter = FileOutputStream(fn, true).buffered().writer()

            fnwriter.use { out ->
                out.write("sdk.dir=${path}\n")
            }

//            fn.withWriterAppend { w ->
//                w << "sdk.dir=${path}\n"
//            }

        } else {
            throw  GradleException(
                    "The file local.properties does not exist, and there is no ANDROID_SDK environmental variable defined in the system.\n" +
                            "Define ANDROID_SDK so it points to the location of the Android SDK, or create the local.properties file manually\n" +
                            "and add the following line to it:\n" +
                            "sdk.dir=<path to Android SDK>")
        }
    }


    val localPropertiesPath = FileInputStream(project.rootProject.file("local.properties"))
    val localProperties = org.jetbrains.kotlin.konan.properties.Properties()
    localProperties.load(localPropertiesPath)
    val sdkDir = localProperties.getProperty("sdk.dir")
    extra["androidPlatformPath"] = "${sdkDir}/platforms/android-${project.extra["targetSdkVersion"]}"
    extra["androidToolsLibPath"] = "${sdkDir}/tools/lib"

    extra["coreZipPath"] = "${rootDir}/mode/processing-core.zip"

    repositories {
        google()
        jcenter()
        maven {
            // mavenCentral() does not work to download the Gradle releases of gradle-tooling-api, one needs
            // to set the artifact url as below to get the latest packages.
            // https://mvnrepository.com/artifact/org.gradle/gradle-tooling-api?repo=gradle-libs-releases-local
            url = uri("https://repo1.maven.org/maven2")
            artifactUrls ("https://repo.gradle.org/gradle/libs-releases-local")
        }

        flatDir {
            dirs ("${project.extra["androidPlatformPath"]}")

        }

        flatDir {
            dirs("${project.extra["androidToolsLibPath"]}")
        }

        flatDir {
            dirs ("${rootDir}/core/dist")
        }
        //flatDir dirs: androidToolsLibPath
        //flatDir dirs: "${rootDir}/core/dist"
    }

//    sourceCompatibility = 1.8
//    targetCompatibility = 1.8

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
        }
    }
}

tasks.clean {
    doFirst {
        delete("dist")
        delete("Docs")
    }
}

tasks.register ("dist") {
//        dependsOn (subprojects.withGroovyBuilder {
//            tasks.build
//        })
    //dependsOn(subprojects.forEach { it.build })
    subprojects.forEach { dependsOn(it.tasks.build) }

    doLast {
        val root = "${buildDir}/zip/AndroidMode"

        // Copy assets to build dir
        FileUtils.copyDirectory(file("mode/templates"), file("${root}/templates"))
        FileUtils.copyDirectory(file("mode/examples"), file("${root}/examples"))
        FileUtils.copyDirectory(file("mode/icons"), file("${root}/icons"))
        FileUtils.copyDirectory(file("mode/theme"), file("${root}/theme"))
        FileUtils.copyDirectory(file("mode/mode"), file("${root}/mode"))
        delete ("${root}/mode/jdi.jar")
        delete ("${root}/mode/jdimodel.jar")

        Files.copy(file("mode/processing-core.zip").toPath(),
                file("${root}/processing-core.zip").toPath(), REPLACE_EXISTING)

        Files.copy(file("mode/keywords.txt").toPath(),
                file("${root}/keywords.txt").toPath(), REPLACE_EXISTING)

        Files.copy(file("mode/version.properties").toPath(),
                file("${root}/version.properties").toPath(), REPLACE_EXISTING)

        Files.copy(file("mode/mode.properties").toPath(),
                file("${root}/mode.properties").toPath(), REPLACE_EXISTING)

        FileUtils.copyDirectory(file("mode/languages"),
                file("${root}/languages"))

        FileUtils.copyDirectory(file("mode/tools/SDKUpdater/tool"),
                file("${root}/tools/SDKUpdater/tool"))
        FileUtils.copyDirectory(file("mode/tools/SDKUpdater/src"),
                file("${root}/tools/SDKUpdater/src"))

        FileUtils.copyDirectory(file("mode/libraries/vr/examples"),
                file("${root}/libraries/vr/examples"))
        FileUtils.copyDirectory(file("mode/libraries/vr/library"),
                file("${root}/libraries/vr/library"))
        FileUtils.copyDirectory(file("mode/libraries/vr/src"),
                file("${root}/libraries/vr/src"))
        Files.copy(file("mode/libraries/vr/library.properties").toPath(),
                file("${root}/libraries/vr/library.properties").toPath(), REPLACE_EXISTING)

        FileUtils.copyDirectory(file("mode/libraries/ar/examples"),
                file("${root}/libraries/ar/examples"))
        FileUtils.copyDirectory(file("mode/libraries/ar/library"),
                file("${root}/libraries/ar/library"))
        FileUtils.copyDirectory(file("mode/libraries/ar/src"),
                file("${root}/libraries/ar/src"))
        Files.copy(file("mode/libraries/ar/library.properties").toPath(),
                file("${root}/libraries/ar/library.properties").toPath(), REPLACE_EXISTING)

        val distFolder = file("dist")
        distFolder.mkdirs()
        ZipUtil.pack(file("${buildDir}/zip"), File("dist/AndroidMode.zip"))
        Files.copy(file("mode/mode.properties").toPath(),
                file("dist/AndroidMode.txt").toPath(), REPLACE_EXISTING)
    }
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.extra["kotlin_version"]}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
