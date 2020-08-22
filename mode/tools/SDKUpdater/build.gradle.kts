import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}
dependencies {
    compile("org.processing:pde:${project.extra["processingVersion"]}")
    compile("com.android.tools:sdklib-${project.extra["toolsLibVersion"]}")
    compile("com.android.tools:repository-${project.extra["toolsLibVersion"]}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.extra["kotlin_version"]}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${project.extra["dokka_version"]}")
}

sourceSets {
    main {
        java {
            srcDirs("src/")
        }

//        kotlin {
//            srcDirs("src/")
//        }
    }
}

val dokka by tasks.getting(DokkaTask::class) {
    outputDirectory = "$rootDir/Docs/SDKUpdater"
    outputFormat = "html"
}

tasks.clean {
    doFirst {
        delete("tool")
    }
}
tasks.build {
    doLast {
        // Copy jar file to tool folder
        val toolJar = file("tool/SDKUpdater.jar");
        toolJar.mkdirs();
        Files.copy(file("$buildDir/libs/SDKUpdater.jar").toPath(),
                toolJar.toPath(), REPLACE_EXISTING);
    }
}
repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}