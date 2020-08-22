import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

//apply plugin: "maven"
//apply plugin: "kotlin"
//apply plugin: "aar"

plugins {
    maven
    id("aar")
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

dependencies {
    implementation("com.android.platform:android:26.0.0")
    implementationAar("com.android.support:support-v4:${project.extra["supportLibsVersion"]}")
    implementationAar("com.google.android.support:wearable:${project.extra["wearVersion"]}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.extra["kotlin_version"]}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${project.extra["dokka_version"]}")
}

task ("createPom") {
    maven.pom {
        withGroovyBuilder {
            "project" {
                setProperty("groupId","org.p5android")
                setProperty("artifactId","processing-core")
                setProperty("version","${project.extra["modeVersion"]}")
                setProperty("packaging","jar")
                "licenses" {
                    "license" {
                        setProperty("name","GNU Lesser General Public License, version 2.1")
                        setProperty("url","https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt")
                        setProperty("distribution","repo")
                    }
                }

                "dependencies" {
                    "dependency" {
                        setProperty("groupId","com.android.support")
                        setProperty("artifactId","support-v4")
                        setProperty("version","${project.extra["supportLibsVersion"]}")
                        setProperty("scope","implementation")
                    }
                    "dependency" {
                        setProperty("groupId","com.google.android.support")
                        setProperty("artifactId","wearable")
                        setProperty("version","${project.extra["wearVersion"]}")
                        setProperty("scope","implementation")
                    }
                }
            }
        }
    }.writeTo("dist/processing-core-${project.extra["modeVersion"]}.pom")
}

sourceSets {
    main {
        java {
            srcDirs ("src/")
        }
        resources {
            srcDirs ("src/")
        }
    }
}

val dokka by tasks.getting(DokkaTask::class) {
    outputDirectory = "$rootDir/Docs/core"
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
        delete ("dist")
        delete ("${project.extra["coreZipPath"]}")
    }
}

tasks.compileJava {
    doFirst {
        val deps = arrayOf("percent.jar",
                "recyclerview-v7.jar",
                "support-compat.jar",
                "support-core-ui.jar",
                "support-core-utils.jar",
                "support-fragment.jar",
                "support-media-compat.jar",
                "support-v4.jar",
                "wearable.jar")
        for (fn in deps) {
            Files.copy(file("${rootDir}/build/libs/" + fn).toPath(),
                    file("${rootDir}/mode/mode/" + fn).toPath(), REPLACE_EXISTING)
        }
    }
}

tasks.build {
    doLast {
        // Copying core jar as zip inside the mode folder
        Files.copy(file("${buildDir}/libs/core.jar").toPath(),
                file("${project.extra["coreZipPath"]}").toPath(), REPLACE_EXISTING)

        // Copying the files for release on JCentral
        val distFolder = file("dist")
        distFolder.mkdirs()
        Files.copy(file("${buildDir}/libs/core.jar").toPath(),
                file("dist/processing-core-${project.extra["modeVersion"]}.jar").toPath(), REPLACE_EXISTING)
        Files.copy(file("${buildDir}/libs/core-sources.jar").toPath(),
                file("dist/processing-core-${project.extra["modeVersion"]}-sources.jar").toPath(), REPLACE_EXISTING)
        Files.copy(file("${buildDir}/libs/core.jar.MD5").toPath(),
                file("dist/processing-core-${project.extra["modeVersion"]}.jar.md5").toPath(), REPLACE_EXISTING)
    }
}

repositories {
    mavenCentral()
    jcenter()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}