import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    maven
    java
    id("aar")
    kotlin("jvm")
    id("org.jetbrains.dokka")
}


dependencies {
    compileOnly("com.android.platform:android:26.0.0")
    compileOnly("org.p5android:processing-core:4.1.1")
    implementationAar("com.google.ar:core:1.12.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.extra["kotlin_version"]}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${project.extra["dokka_version"]}")
}

task("createPom") {
    maven.pom {
        withGroovyBuilder {
            "project" {
                setProperty("groupId", "org.p5android")
                setProperty("artifactId", "processing-ar")
                setProperty("version", "${project.extra["arLibVersion"]}")
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
                        setProperty("groupId", "com.google.ar")
                        setProperty("artifactId", "core")
                        setProperty("version", "${project.extra["garVersion"]}")
                        setProperty("scope", "implementation")
                    }
                }
            }
        }
    }.writeTo("dist/processing-ar-${project.extra["arLibVersion"]}.pom")
}

sourceSets {
    main {
        java {
            srcDirs("src/")
        }
        resources {
            srcDirs("src/")
        }
    }
}

val dokka by tasks.getting(DokkaTask::class) {
    outputDirectory = "$rootDir/Docs/ar"
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
        delete("library/ar.jar")
    }
}

tasks.compileJava {
    doFirst {
        val deps = arrayOf("core.jar")
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
        // Copying ar jar to library folder
        val arJar = file("library/ar.jar")
        arJar.mkdirs();
        Files.copy(file("$buildDir/libs/ar.jar").toPath(),
                arJar.toPath(), REPLACE_EXISTING);

        // // Copying the files for release on JCentral
        val distFolder = file("dist");
        distFolder.mkdirs();
        Files.copy(file("$buildDir/libs/ar.jar").toPath(),
                file("dist/processing-ar-${project.extra["arLibVersion"]}.jar").toPath(), REPLACE_EXISTING);
        Files.copy(file("$buildDir/libs/ar-sources.jar").toPath(),
                file("dist/processing-ar-${project.extra["arLibVersion"]}-sources.jar").toPath(), REPLACE_EXISTING);
        Files.copy(file("$buildDir/libs/ar.jar.MD5").toPath(),
                file("dist/processing-ar-${project.extra["arLibVersion"]}.jar.md5").toPath(), REPLACE_EXISTING);
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