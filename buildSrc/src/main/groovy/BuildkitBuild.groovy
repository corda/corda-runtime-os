import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.SkipWhenEmpty
import java.nio.file.Path
import static org.gradle.api.tasks.PathSensitivity.RELATIVE
import javax.inject.Inject
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

// Task to publish worker images to artifactory using buildkit image builder

// Requires buildctl client installed and a working buildkit pod from aws cluster (TBD)

// More info available here https://r3-cev.atlassian.net/wiki/spaces/CB/pages/4063035406/BuildKit

abstract class BuildkitBuild extends DefaultTask {

    @Inject
    protected abstract ObjectFactory getObjects()

    @Input
    final Property<Boolean> useShortName =
        getObjects().property(Boolean).convention(false)

    @Input
    final Property<String> baseImageName =
        getObjects().property(String).convention('azul/zulu-openjdk')

    @Input
    final Property<String> baseImageTag =
        getObjects().property(String).convention('11')
    @Input
    final ListProperty<String> arguments =
        getObjects().listProperty(String)

    @PathSensitive(RELATIVE)
    @SkipWhenEmpty
    @InputFiles
    final ConfigurableFileCollection sourceFiles =
        getObjects().fileCollection()

    @PathSensitive(RELATIVE)
    @InputFiles
    final ConfigurableFileCollection extraSourceFiles =
        getObjects().fileCollection()

    @PathSensitive(RELATIVE)
    @InputFiles
    final ConfigurableFileCollection jdbcDriverFiles =
        getObjects().fileCollection()

    @Input
    final Property<String> subDir =
        getObjects().property(String).convention('')


    @TaskAction
    def checkForDaemon() {

        try {
            (new Socket('127.0.0.1', 3476)).close();
            logger.quiet("Buildkit daemon found")
        }
        catch(SocketException e) {
            throw new GradleException("No daemon found. Please connect to available buildkit daemon (and port forward it to 3476) and start again")
        }

    }

    // Copy fat jars and jdbc drivers to temporary directory 
    // Runs before the buildctl command execution

    @TaskAction
    def CopyFiles() {

        def buildBaseDir = temporaryDir.toPath()
        def containerizationDir = Paths.get("$buildBaseDir/containerization/")
        def driverDir = Paths.get("$buildBaseDir/jdbc-driver/")

        if (!(Files.exists(containerizationDir))) {
            logger.quiet("Created containerization dir")
            Files.createDirectories(containerizationDir)
        }

        if (!(Files.exists(driverDir))) {
            logger.quiet("Created jdbc-driver dir")
            Files.createDirectories(driverDir)
        }

        sourceFiles.forEach{
            def jarName = useShortName
                    ? it.name.replace("corda-", "").replace("-${project.version}", "")
                    : it.name
            if(Files.exists(Paths.get(it.path))){
                Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}/$jarName"), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        extraSourceFiles.forEach{
            def jarName = useShortName
                    ? it.name.replace("corda-", "").replace("-${project.version}", "")
                    : it.name
            if(Files.exists(Paths.get(it.path))){
                Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}/$jarName"), StandardCopyOption.REPLACE_EXISTING)
            }          
        }

        jdbcDriverFiles.forEach{
            def jarName = it.name
            if(Files.exists(Paths.get(it.path))){
                Files.copy(Paths.get(it.path), Paths.get("${driverDir.toString()}/$jarName"), StandardCopyOption.REPLACE_EXISTING)
            }           
        }
    }
}
