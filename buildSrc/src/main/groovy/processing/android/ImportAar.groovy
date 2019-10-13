package processing.android

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

/**
 * Build Gradle plgin needed to use aar files as dependencies in a pure java library project. 
 * Adapted from the following plugin by nekocode
 * https://github.com/nekocode/Gradle-Import-Aar
 * Ported to Groovy, and made specific to the needs of the Android mode build process (i.e.: this plugin
 * is not meant to be used with other projects).
 */
class ImportAar implements Plugin<Project> {
    
    final String CONFIG_NAME_POSTFIX = "Aar"
    
    void apply(Project project) {
        def aar = AndroidArtifacts.TYPE_AAR
        def jar = AndroidArtifacts.TYPE_JAR
        def exp = AndroidArtifacts.TYPE_EXPLODED_AAR

        // // Create aar configurations
        Collection<Configuration> allConfigs = project.getConfigurations().toList()
        for (Configuration config: allConfigs) {
            Configuration aarConfig = project.configurations.maybeCreate(config.name + CONFIG_NAME_POSTFIX)
            
            // Add extracted jars to original configuration after project evaluating
            aarConfig.getAttributes().attribute(ARTIFACT_FORMAT, jar)
            project.afterEvaluate {
            	for (File jarFile: aarConfig) {
            		// print "================================================> FILE "
            		// println jarFile
            		// println jarFile.getName()
            		// for (String s: project.sourceSets.main.compileClasspath) {
            		// 	println s
            		// }
            		// project.getDependencies().add(config.name, project.files(jarFile))

                    // Add jar file to classpath
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

            // Tell Idea about our aar configuration
            PluginManager pluginManager = project.getPluginManager()
            pluginManager.apply(IdeaPlugin.class)
            ExtensionContainer extensions = project.getExtensions()
            // IdeaModel model = extensions.getByType​(IdeaModel.class)
            IdeaModel model = extensions.getByName("idea")
            IdeaModule module = model.getModule()
            module.scopes.PROVIDED.plus += [aarConfig]
        }

        // Register aar transform
        project.dependencies {
            registerTransform {
                from.attribute(ARTIFACT_FORMAT, aar)
                to.attribute(ARTIFACT_FORMAT, jar)
                artifactTransform(AarJarArtifactTransform.class)
            }
        }
    }

    static class AarJarArtifactTransform extends ArtifactTransform {
        @Override
        List<File> transform(File file) {
        	// println "Transforming---------------------------------" 
        	// println outputDirectory
        	// println file
        	File explodedDir = new File(getOutputDirectory(), "exploded")
        	// println explodedDir

        	AarExtractor aarExtractor = new AarExtractor()
        	aarExtractor.extract(file, explodedDir)
        	File classesJar = new File(new File(explodedDir, FD_JARS), FN_CLASSES_JAR)
        	// println classesJar

            // String[] names = file.getPath().split(Pattern.quote(File.separator))            
            // print "NAMES "
            // println names
            // print "NAME "
            // println file.getName()
            // String aarName = names[names.length - 4].replace(".aar", "")
            String aarName = file.getName().replace(".aar", "")
            // print "AAR NAME "
            // println aarName
            File renamedJar = new File(getOutputDirectory(), aarName + ".jar")
            renamedJar << classesJar.bytes

            return ImmutableList.of(renamedJar)
       }
    }
}