package processing.test

// https://docs.gradle.org/current/javadoc/index.html?overview-summary.html
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.ArtifactTransform

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT

// https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/dependency/ExtractAarTransform.kt
import com.android.build.gradle.internal.dependency.AarTransform
import com.android.build.gradle.internal.dependency.ExtractAarTransform
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.aar.AarExtractor
import com.google.common.collect.ImmutableList
import java.util.regex.Pattern
import static com.android.SdkConstants.FD_JARS
import static com.android.SdkConstants.FN_CLASSES_JAR

import java.io.File
import java.nio.file.Files
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

class GreetingPlugin implements Plugin<Project> {
    
    final String CONFIG_NAME_POSTFIX = "Aar"
    

    void apply(Project project) {

        // // Check if this project is a pure java project
        // val java = project.convention.findPlugin(JavaPluginConvention::class.java)
        // if (java == null || project.plugins.findPlugin(AndroidBasePlugin::class.java) != null) {
        //     throw IllegalStateException("The 'import-aar' plugin can only be used in a pure java module.")
        // }

        // val aar = AndroidArtifacts.ArtifactType.AAR.type
        // val jar = AndroidArtifacts.ArtifactType.JAR.type

        def aar = AndroidArtifacts.TYPE_AAR
        def jar = AndroidArtifacts.TYPE_JAR
        def exp = AndroidArtifacts.TYPE_EXPLODED_AAR

        // // Create aar configurations
        Collection<Configuration> allConfigs = project.getConfigurations().toList()
        for (Configuration config: allConfigs) {
        	// println config.name
            Configuration aarConfig = project.configurations.maybeCreate(config.name + CONFIG_NAME_POSTFIX)
            
        
            // Add extracted jars to original configuration after project evaluating
            aarConfig.getAttributes().attribute(ARTIFACT_FORMAT, jar)
            project.afterEvaluate {
            	for (File jarFile: aarConfig) {
            		print "================================================> FILE "
            		println jarFile
            		println jarFile.getName()
            		// for (String s: project.sourceSets.main.compileClasspath) {
            		// 	println s
            		// }
            		// project.getDependencies().add(config.name, project.files(jarFile))
            		project.sourceSets.main.compileClasspath += project.files(jarFile)

                    File libraryFolder = new File(System.getProperty("user.dir"), "build/libs")
                    libraryFolder.mkdirs()
            
                    // Strip version number when copying
                    String name = jarFile.getName()
                    int p = name.lastIndexOf("-") 
                    String libName = name.substring(0, p) + ".jar"
                    File libraryJar = new File(libraryFolder, libName)
                    Files.copy(jarFile.toPath(), libraryJar.toPath(), REPLACE_EXISTING)              		
            	}

            }
            // aarConfig.attributes.attribute(ARTIFACT_FORMAT, jar)
            // project.afterEvaluate {
            //     aarConfig.forEach { jarFile ->
            //         project.dependencies.add(config.name, project.files(jarFile))
            //     }
            // }


            // Tell Idea about our aar configuration
            PluginManager pluginManager = project.getPluginManager()
            pluginManager.apply(IdeaPlugin.class)
            ExtensionContainer extensions = project.getExtensions()
            // IdeaModel model = extensions.getByType​(IdeaModel.class)
            IdeaModel model = extensions.getByName("idea")
            IdeaModule module = model.getModule()
            module.scopes.PROVIDED.plus += [aarConfig]

            // project.pluginManager.apply(IdeaPlugin::class.java)
            // project.extensions.getByType(IdeaModel::class.java)
            //         .module.scopes["PROVIDED"]!!["plus"]!!.add(aarConfig)

            
        }
        // Register aar transform
        project.dependencies {
            registerTransform {
                from.attribute(ARTIFACT_FORMAT, aar)
                to.attribute(ARTIFACT_FORMAT, jar)
                artifactTransform(AarJarArtifactTransform.class)
            }

            // registerTransform {
            //     from.attribute(ARTIFACT_FORMAT, aar)
            //     to.attribute(ARTIFACT_FORMAT, exp)
            //     artifactTransform(ExtractAarTransform)
            // }

            // registerTransform {
            //     from.attribute(ARTIFACT_FORMAT, exp)
            //     to.attribute(ARTIFACT_FORMAT, "classes.jar")
            //     artifactTransform(AarTransform) { params(jar) }
            // }

            // registerTransform {
            //     from.attribute(ARTIFACT_FORMAT, "classes.jar")
            //     to.attribute(ARTIFACT_FORMAT, jar)
            //     artifactTransform(AarJarArtifactTransform)
            // }
        }









        // project.dependencies.run {
        //     registerTransform {
        //         it.from.attribute(ARTIFACT_FORMAT, aar)
        //         it.to.attribute(ARTIFACT_FORMAT, jar)
        //         it.artifactTransform(AarJarArtifactTransform::class.java)
        //     }
    }



    // class AarJarArtifactTransform : ArtifactTransform() {

    //     override fun transform(input: File): MutableList<File> {
    //         val extractTrans = ExtractAarTransform()
    //         extractTrans.outputDirectory = File(outputDirectory, "exploded")
    //         var files = extractTrans.transform(input)

    //         val aarTrans = AarTransform(AndroidArtifacts.ArtifactType.JAR, false, false)
    //         aarTrans.outputDirectory = outputDirectory
    //         files = files.flatMap { aarTrans.transform(it) }

    //         // Copy and rename the classes.jar
    //         val jarFile = files.singleOrNull() ?: return arrayListOf()
    //         val renamedJarFile = File(outputDirectory, "${input.nameWithoutExtension}.jar")
    //         renamedJarFile.writeBytes(jarFile.readBytes())

    //         return arrayListOf(renamedJarFile)
    //     }
    // }

    static class AarJarArtifactTransform extends ArtifactTransform {
        @Override
        List<File> transform(File file) {
        	println "Transforming---------------------------------" 
        	println outputDirectory
        	println file
        	File explodedDir = new File(getOutputDirectory(), "exploded")
        	println explodedDir

        	AarExtractor aarExtractor = new AarExtractor()
        	aarExtractor.extract(file, explodedDir)
        	File classesJar = new File(new File(explodedDir, FD_JARS), FN_CLASSES_JAR)
        	println classesJar

            // String[] names = file.getPath().split(Pattern.quote(File.separator))            
            // print "NAMES "
            // println names
            // print "NAME "
            // println file.getName()
            // String aarName = names[names.length - 4].replace(".aar", "")
            String aarName = file.getName().replace(".aar", "")
            print "AAR NAME "
            println aarName
            File renamedJar = new File(getOutputDirectory(), aarName + ".jar")
            renamedJar << classesJar.bytes


          

            // ExtractAarTransform extractTrans = new ExtractAarTransform(explodedDir)

            // copy {
            //     from zipTree(file)
            //     include 'classes.jar'
            //     into explodedDir
            //     rename { String fileName ->
            //              fileName.replace('classes.jar', baseFilename + '.jar')
            //     }
            // }



         //    extractTrans.outputDirectory = File(outputDirectory, "exploded")
         //    files = extractTrans.transform(file)

            // aarTrans = AarTransform(jar, false, false)
         //    aarTrans.outputDirectory = outputDirectory
         //    files = files.flatMap { aarTrans.transform(it) }

         //    // Copy and rename the classes.jar
         //    jarFile = files.singleOrNull()
         //    renamedJarFile = File(outputDirectory, "${input.nameWithoutExtension}.jar")
         //    renamedJarFile.writeBytes(jarFile.readBytes())

            // return arrayListOf(renamedJarFile)
            return ImmutableList.of(renamedJar)


        	
        	/*
            println "Transforming---------------------------------" 
            print "SRC "
            println file
            final String[] names = file.getPath().split(Pattern.quote(File.separator))
            final String aarName = names[names.length - 4].replace(".aar", "")
            print "AAR NAME "
            println aarName
            final File renamedJar = new File(getOutputDirectory(), aarName + ".jar")
            renamedJar << file.bytes

            

            File libraryFolder = new File(System.getProperty("user.dir"), "build/libs")
            libraryFolder.mkdirs()
            final File libraryJar = new File(libraryFolder, aarName + ".jar")
            Files.copy(renamedJar.toPath(), libraryJar.toPath(), REPLACE_EXISTING)


            print "DST "
            println renamedJar

            println "----------------------------------------DONE"
            return ImmutableList.of(renamedJar)
            */
            
           // return ImmutableList.of(file)
       }
    }



}