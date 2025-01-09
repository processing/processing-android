// import org.gradle.api.Plugin
// import org.gradle.api.Project
// import org.gradle.api.artifacts.Configuration
// import org.gradle.api.artifacts.transform.TransformAction
// import org.gradle.api.artifacts.transform.TransformParameters
// import org.gradle.api.artifacts.transform.InputArtifact
// import org.gradle.api.artifacts.transform.TransformOutputs
// import org.gradle.api.file.FileSystemLocation
// import org.gradle.api.provider.Provider
// import org.gradle.api.tasks.PathSensitive
// import org.gradle.api.tasks.PathSensitivity

// import org.gradle.api.attributes.LibraryElements
// import org.gradle.api.attributes.Usage
// import org.gradle.api.attributes.Category

// import com.android.build.gradle.internal.dependency.AarTransform
// import com.android.build.gradle.internal.dependency.ExtractAarTransform
// import com.android.build.gradle.internal.publishing.AndroidArtifacts
// import com.android.builder.aar.AarExtractor
// import com.google.common.collect.ImmutableList

// import java.nio.file.Files
// import static java.nio.file.StandardCopyOption.REPLACE_EXISTING


// import org.gradle.api.Plugin
// import org.gradle.api.Project
// import org.gradle.api.artifacts.Configuration
// import org.gradle.api.artifacts.ResolvedArtifact
// import java.util.zip.ZipFile

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * Build Gradle plgin needed to use aar files as dependencies in a pure java library project. 
 * Adapted from the following plugin by nekocode
 * https://github.com/nekocode/Gradle-Import-Aar
 * Ported to Groovy, and made specific to the needs of the Android mode build process (i.e.: this plugin
 * is not meant to be used with other projects).
 * Ported to Gradle 8 replacing the deprecated ArtifactTransform with the new TransformAction API.
 */
class ImportAar implements Plugin<Project> {
    final String CONFIG_NAME_POSTFIX = "Aar"

    @Override
    void apply(Project project) {
        // def aar = AndroidArtifacts.TYPE_AAR
        // def jar = AndroidArtifacts.TYPE_JAR

        println ">>> Calling ImportAar"

        // Create a custom resolvable configuration
        project.configurations.create('aarExtractorResolvable') {
            canBeResolved = true
            canBeConsumed = false
            extendsFrom project.configurations.implementation
        }

        project.tasks.register('extractAarJars', ExtractAarJarsTask) {
            group = 'build'
            description = 'Extracts JAR files from AAR dependencies and places them in build/libs.'
        }


        // project.task('extractAarJars') {
        //     doLast {
        //         println "=======> Calling extractAarJars task"

        //         project.configurations.each { Configuration config ->
        //             config.resolvedConfiguration.resolvedArtifacts.each { ResolvedArtifact artifact ->
        //             println "Resolved artifact: ${artifact}"
        //                 if (artifact.type == 'aar') {
        //                     extractJarFromAar(artifact, project)
        //                 }
        //             }
        //         }
        //     }
        // }
    


/*
        // Create AAR configurations
        Collection<Configuration> allConfigs = project.getConfigurations().toList()
        for (Configuration config: allConfigs) {
            println config
            Configuration aarConfig = project.configurations.maybeCreate(config.name + CONFIG_NAME_POSTFIX)            
            println aarConfig

            // Add extracted jars to original configuration after project evaluating
            aarConfig.attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            }

            project.afterEvaluate {
                println "-> In afterEvaluate"                
                aarConfig.resolvedConfiguration.resolvedArtifacts.each { artifact ->
                    File jarFile = artifact.file
                    print "================================================> FILE "
                    println jarFile
                    println jarFile.getName()

                    // Add jar file to classpath
                    project.sourceSets.main.compileClasspath += project.files(jarFile)

                    File libraryFolder = new File(project.buildDir, "libs")
                    libraryFolder.mkdirs()
            
                    // Strip version number when copying
                    String name = jarFile.name
                    int p = name.lastIndexOf("-") 
                    String libName = name.substring(0, p) + ".jar"
                    File libraryJar = new File(libraryFolder, libName)
                    Files.copy(jarFile.toPath(), libraryJar.toPath(), REPLACE_EXISTING)              		
                }
            }
        }
 
        // Register aar transform
        project.dependencies {
            registerTransform(AarToJarTransform) {
                from.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, aar))
                to.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, jar))
            }
        }
        */
    }

    // void extractJarFromAar(ResolvedArtifact artifact, Project project) {
    //     println "Input AAR: ${aarFile}"
    //     def aarFile = artifact.file
    //     def zipFile = new ZipFile(aarFile)
    //     def entry = zipFile.getEntry('classes.jar')
    //     if (entry) {
    //         project.copy {
    //             from project.zipTree(aarFile)
    //             include 'classes.jar'
    //             into "${project.buildDir}/libs"
    //             rename { "${artifact.name}-${artifact.moduleVersion.id.version}.jar" }
    //         }
    //     }
    //     zipFile.close()
    // }
    

    // abstract static class AarToJarTransform implements TransformAction<TransformParameters.None> {
    //     AarToJarTransform() {
    //         println "AarToJarTransform instantiated"
    //     }

    //     @InputArtifact
    //     @PathSensitive(PathSensitivity.NAME_ONLY)
    //     abstract Provider<FileSystemLocation> getInputArtifact()

    //     @Override
    //     void transform(TransformOutputs outputs) {
    //         File inputFile = inputArtifact.get().asFile
    //         println "Input AAR: ${inputFile}"
    //         File explodedDir = new File(outputs.getOutputDirectory(), "exploded")
    //         println "Exploded Directory: ${explodedDir}"

    //         AarExtractor aarExtractor = new AarExtractor()
    //         aarExtractor.extract(inputFile, explodedDir)
    //         File classesJar = new File(new File(explodedDir, "jars"), "classes.jar")
    //         if (classesJar.exists()) {
    //             println "Classes JAR found: ${classesJar}"
    //             String aarName = inputFile.name.replace(".aar", "")
    //             File renamedJar = outputs.file("${aarName}.jar")
    //             Files.copy(classesJar.toPath(), renamedJar.toPath(), REPLACE_EXISTING)
    //             println "Transformed JAR: ${renamedJar}"
    //         } else {
    //             println "Error: classes.jar not found in ${explodedDir}"
    //         }
    //     }
    // }
}


class ExtractAarJarsTask extends org.gradle.api.DefaultTask {
    @TaskAction
    void extractJars() {
        File outputDir = new File(project.buildDir, 'libs')
        outputDir.mkdirs()

        // Configuration compileClasspath = project.configurations.getByName('implementation')
        Configuration aarExtractorResolvable = project.configurations.getByName('aarExtractorResolvable')

        // compileClasspath.resolvedConfiguration.resolvedArtifacts.each { artifact ->
        aarExtractorResolvable.resolvedConfiguration.resolvedArtifacts.each { artifact ->        
            if (artifact.type == 'aar') {
                File aarFile = artifact.file
                println "Processing AAR: ${aarFile.name}"
                
                // Extract the AAR file
                ZipFile zipFile = new ZipFile(aarFile)
                zipFile.entries().each { entry ->
                    if (entry.name.endsWith('.jar')) {
                       println "Classes JAR found: ${entry}"
                       String aarName = aarFile.name.replace(".aar", "")
    
                        File jarOutput = new File(outputDir, "${aarName}.jar")
                        jarOutput.parentFile.mkdirs()
                        
                        // Write the JAR file to the output directory
                        zipFile.getInputStream(entry).withCloseable { inputStream ->
                            jarOutput.withOutputStream { outputStream ->
                                copyStream(inputStream, outputStream)
                            }
                        }
                        println "Extracted JAR: ${jarOutput.absolutePath}"
                    }
                }
            }
        }
    }

    /**
     * Copies data from an InputStream to an OutputStream.
     */
    void copyStream(InputStream input, OutputStream output) {
        byte[] buffer = new byte[1024]
        int bytesRead
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }
}

