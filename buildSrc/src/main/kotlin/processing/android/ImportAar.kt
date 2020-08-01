package processing.android

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.aar.AarExtractor
import  com.android.SdkConstants.FD_JARS
import  com.android.SdkConstants.FN_CLASSES_JAR
import java.io.File
import java.nio.file.Files
import java.nio.file.Files.copy
import  java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Build Gradle plgin needed to use aar files as dependencies in a pure java library project.
 * Adapted from the following plugin by nekocode
 * https://github.com/nekocode/Gradle-Import-Aar
 * Ported to Groovy, and made specific to the needs of the Android mode build process (i.e.: this plugin
 * is not meant to be used with other projects).
 */
open class ImportAar : Plugin<Project> {

    companion object {
        const val  CONFIG_NAME_POSTFIX = "Aar"
    }

    override fun apply(project: Project) {
        val aar = AndroidArtifacts.ArtifactType.AAR.type
        val jar = AndroidArtifacts.ArtifactType.JAR.type
        val exp = AndroidArtifacts.ArtifactType.EXPLODED_AAR.type

        // // Create aar configurations
        val  allConfigs = project.configurations.toList()
        for ( config in allConfigs) {
            val aarConfig = project.configurations.maybeCreate(config.name + CONFIG_NAME_POSTFIX)

            // Add extracted jars to original configuration after project evaluating
            aarConfig.attributes.attribute(ARTIFACT_FORMAT, jar)

//            project.afterEvaluate {
//            	for (jarFile in aarConfig) {
//            		// print "================================================> FILE "
//            		// println jarFile
//            		// println jarFile.getName()
//            		// for (String s: project.sourceSets.main.compileClasspath) {
//            		// 	println s
//            		// }
//            		// project.getDependencies().add(config.name, project.files(jarFile))
//
//
//                    // Add jar file to classpath
//             		 project.getSourceSets().main.compileClasspath = project.getSourceSets().main.compileClasspath +  project.files(jarFile)
//
//
//
            val libraryFolder =  File(System.getProperty("user.dir"), "build/libs")
            libraryFolder.mkdirs()
//
//                    // Strip version number when copying
//                    val name = jarFile.name
//                    val p = name.lastIndexOf("-")
//                    val libName = name.substring(0, p) + ".jar"
//                    val libraryJar = File(libraryFolder, libName)
//                    Files.copy(jarFile.toPath(), libraryJar.toPath(), REPLACE_EXISTING)
//            	}
//
//            }

            project.afterEvaluate {
                aarConfig.forEach { jarFile ->
                    project.dependencies.add(config.name, project.files(jarFile))
                    val name = jarFile.name
                    val p = name.lastIndexOf("-")
                    val libName = name.substring(0,p) + ".jar"
                    val libraryJar = File(libraryFolder,libName)
                    Files.copy(jarFile.toPath(),libraryJar.toPath(),REPLACE_EXISTING)
                }
            }

            // Tell Idea about our aar configuration
            project.pluginManager.apply(IdeaPlugin::class.java)

            project.extensions.getByType(IdeaModel::class.java)
                    .module.scopes["PROVIDED"]!!["plus"]!!.add(aarConfig)

        }

        // Register aar transform
        project.dependencies.run {
            registerTransform {
                it.from.attribute(ARTIFACT_FORMAT, aar)
                it.to.attribute(ARTIFACT_FORMAT, jar)
                it.artifactTransform(AarJarArtifactTransform::class.java)
            }
        }
    }

    class AarJarArtifactTransform : ArtifactTransform() {

        override fun transform(file: File): MutableList<File> {
            // println "Transforming---------------------------------"
            // println outputDirectory
            // println file
            val explodedDir = File(getOutputDirectory(), "exploded")
            // println explodedDir

            val aarExtractor = AarExtractor()
            aarExtractor.extract(file, explodedDir)
            var classesJar =  File(File(explodedDir, FD_JARS), FN_CLASSES_JAR)
            // println classesJar

            // String[] names = file.getPath().split(Pattern.quote(File.separator))
            // print "NAMES "
            // println names
            // print "NAME "
            // println file.getName()
            // String aarName = names[names.length - 4].replace(".aar", "")
            var aarName = file.getName().replace(".aar", "")
            // print "AAR NAME "
            // println aarName
            var renamedJar = File(getOutputDirectory(), aarName + ".jar")
            renamedJar.writeBytes(classesJar.readBytes())

            return arrayListOf(renamedJar)
        }
    }
}