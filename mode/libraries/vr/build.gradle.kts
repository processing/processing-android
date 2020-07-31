
import java.nio.file.Files
import  java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

//apply plugin: "maven"
//apply plugin: "kotlin"
//apply plugin: "aar"

plugins {
    maven
    java
    id("aar")
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

dependencies {
    compileOnly("com.android.platform:android:26.0.0")
    compileOnly("org.p5android:processing-core:${project.extra["modeVersion"]}")
    implementationAar("com.google.vr:sdk-audio:${project.extra["gvrVersion"]}")
    implementationAar("com.google.vr:sdk-base:${project.extra["gvrVersion"]}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.extra["kotlin_version"]}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${project.extra["dokka_version"]}")
}

task("createPom") {
    maven.pom {
        withGroovyBuilder {
            "project" {
                setProperty("groupId", "org.p5android")
                setProperty("artifactId", "processing-vr")
                setProperty("version", "${project.extra["vrLibVersion"]}")
                setProperty("packaging", "jar")

                "licenses" {
                    "license" {
                        setProperty("name", "GNU Lesser General Public License, version 2.1")
                        setProperty("url", "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt")
                        setProperty("distribution", "repo")
                    }
                }

                "dependencies" {
                    "dependency" {
                        setProperty("groupId", "org.p5android")
                        setProperty("artifactId", "processing-core")
                        setProperty("version", "${project.extra["modeVersion"]}")
                        setProperty("scope", "implementation")
                    }

                    "dependency" {
                        setProperty("groupId", "com.google.vr")
                        setProperty("artifactId", "sdk-base")
                        setProperty("version", "${project.extra["gvrVersion"]}")
                        setProperty("scope", "implementation")
                    }

                    "dependency" {
                        setProperty("groupId", "com.google.vr")
                        setProperty("artifactId", "sdk-audio")
                        setProperty("version", "${project.extra["gvrVersion"]}")
                        setProperty("scope", "implementation")
                    }
                }
            }
        }
    }.writeTo("dist/processing-vr-${project.extra["vrLibVersion"]}.pom")
}

sourceSets {
    main {
        java {
            srcDirs("src/")
        }
    }
}

val dokka by tasks.getting(DokkaTask::class) {
    outputDirectory = "$buildDir/dokka"
    outputFormat = "html"
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn("classes")
    classifier = "sources"
    from("sourceSets.main.allSource")
}

// Does not work because of Processing-specific tags in source code, such as @webref
tasks.register<Jar>("javadocJar") {
    dependsOn("javadoc")
    classifier = "javadoc"
    from("javadoc.destinationDir")
}

artifacts {
//     archives javadocJar
    add("archives", sourcesJar)
}

tasks.jar {
    doLast {
        ant.withGroovyBuilder {
            "checksum"("file" to archivePath)
        }
    }
}

tasks.clean {
    doFirst {
        delete("dist")
        delete("library/vr.jar")
    }
}


tasks.compileJava {
    doFirst {
        val deps = arrayOf("sdk-audio.jar",
                "sdk-base.jar",
                "sdk-common.jar")

        val libFolder = file("library")
        libFolder.mkdirs()
        for (fn in deps) {
            Files.copy(file("${rootDir}/build/libs/" + fn).toPath(),
                    file("library/" + fn).toPath(), REPLACE_EXISTING);
        }
    }
}


tasks.build {
    doLast {
        // // Copying vr jar to library folder
        val vrJar = file("library/vr.jar")
        vrJar.mkdirs();
        Files.copy(file("$buildDir/libs/vr.jar").toPath(),
                vrJar.toPath(), REPLACE_EXISTING);

        // // Copying the files for release on JCentral
        val distFolder = file("dist");
        distFolder.mkdirs();
        Files.copy(file("$buildDir/libs/vr.jar").toPath(),
                file("dist/processing-vr-${project.extra["vrLibVersion"]}.jar").toPath(), REPLACE_EXISTING);
        Files.copy(file("$buildDir/libs/vr-sources.jar").toPath(),
                file("dist/processing-vr-${project.extra["vrLibVersion"]}-sources.jar").toPath(), REPLACE_EXISTING);
        Files.copy(file("$buildDir/libs/vr.jar.MD5").toPath(),
                file("dist/processing-vr-${project.extra["vrLibVersion"]}.jar.md5").toPath(), REPLACE_EXISTING);
    }
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}