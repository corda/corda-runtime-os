import org.gradle.api.tasks.Exec
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

abstract class BuildkitBuild extends Exec {

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

    @Input
    final Property<String> containerTag =
        getObjects().property(String).convention('')

    @Input
    final Property<String> containerName =
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

    @Override
    @TaskAction
    protected void exec(){

        def buildBaseDir = temporaryDir.toPath()
        def containerizationDir = Paths.get("$buildBaseDir/containerization/")
        def driverDir = Paths.get("$buildBaseDir/jdbc-driver/")
        def containerLocation = '/opt/override/'
        def driverLocation = '/opt/jdbc-driver'

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

        List<String> javaArgs = new ArrayList<String>(arguments.get())
        javaArgs.add("-Dlog4j2.debug=\${ENABLE_LOG4J2_DEBUG:-false}")
        javaArgs.add("-Dlog4j.configurationFile=log4j2-console.xml")
        javaArgs.add("-Dpf4j.pluginsDir=${containerLocation + subDir.get()}")

        def baseImageName = "${baseImageTag.get().empty ? baseImageName.get() : "${baseImageName.get()}:${baseImageTag.get()}"}"
        def finalName = "${containerTag.get().isEmpty() ? containerName.get() : "${containerName.get()}:${containerTag.get()}"}"

        String systemCommand
        String systemPrefix
        if (System.properties['os.name'].toLowerCase().contains('windows')) {
            systemCommand = 'powershell'
            systemPrefix = '/c'
        } else{
            systemCommand = 'bash'
            systemPrefix = '-c'
        }

        String[] baseCommand = ['buildctl', "--addr tcp://localhost:3476" , "build" ,"--frontend=dockerfile.v0" , "--local context=/" , "--local dockerfile=${project.rootDir.toString() + "/docker"}"]
        String[] opts = ["--opt build-arg:BASE_IMAGE=${baseImageName}" , "--opt build-arg:BUILD_PATH=${containerizationDir}" , "--opt build-arg:JAR_LOCATION=${containerLocation + subDir.get()}" , "--opt build-arg:JDBC_PATH=${driverDir}" , "--opt build-arg:JDBC_DRIVER_LOCATION=${driverLocation}" , "--opt build-arg:IMAGE_ENTRYPOINT=\"exec java ${javaArgs.join(" ")} -jar  ${containerLocation}cli.jar\" "]
        String[] commandTail = ["--output type=image,name=docker-js-temp.software.r3.com/corda-os-${finalName},push=true" , "--export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-${containerName.get()}-cache" , "--import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-${containerName.get()}-cache"]
        
        String[] buildkitCommand = baseCommand + opts + commandTail

        String finalCommand = buildkitCommand.join("\n")
        println("$finalCommand")

        commandLine systemCommand, systemPrefix, buildkitCommand.join(" ")    
        super.exec()
    }
}