import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.SkipWhenEmpty

import java.nio.file.Path

import static org.gradle.api.tasks.PathSensitivity.RELATIVE

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

// Task to publish worker images to artifactory using buildkit image builder

// Requires buildctl client installed and a working buildkit pod from aws cluster (TBD)

// More info available here https://r3-cev.atlassian.net/wiki/spaces/CB/pages/4063035406/BuildKit

abstract class BuildkitBuild extends Exec {

    private static final String CONTAINER_LOCATION = "/opt/override/"
    private final String projectName = project.name
    private def copyTask


    @Inject
    protected abstract ObjectFactory getObjects()

    @Input
    final Property<Boolean> setEntry =
            getObjects().property(Boolean).convention(true)

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

    @Input
    final Property<String> overrideEntryName =
            getObjects().property(String).convention('')

    @Input
    final Property<String> overrideContainerName =
            getObjects().property(String).convention('')

    @Input
    final Property<String> subDir =
            getObjects().property(String).convention('')

    BuildkitBuild(){

        workingDir("${project.rootDir}")

        def containerizationDir = temporaryDir.toPath()

        copyTask = project.tasks.register('copyFiles', CopyFiles, sourceFiles, extraSourceFiles, useShortName, temporaryDir.toPath())
        super.dependsOn(copyTask)

//        extraSourceFiles.forEach{
//            logger.quiet("${it.toString()}")
//            Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}"), StandardCopyOption.REPLACE_EXISTING)
//        }

        List<String> javaArgs = new ArrayList<String>(arguments.get())
        javaArgs.add("-Dlog4j2.debug=\${ENABLE_LOG4J2_DEBUG:-false}")
        javaArgs.add("-Dlog4j.configurationFile=\${LOG4J_CONFIG_FILE}")

        def imageName = "${baseImageTag.get().empty ? baseImageName.get() : "${baseImageName.get()}:${baseImageTag.get()}"}"

        String containerName = overrideContainerName.get().empty ? projectName : overrideContainerName.get()

            logger.quiet("\n\nRunning Command \n buildctl --addr tcp://localhost:3476 build --frontend=dockerfile.v0 --local context=/ --local dockerfile=${project.rootDir.toString() +  "/docker"}  --opt build-arg:BASE_IMAGE=${imageName} --opt build-arg:BUILD_PATH=${containerizationDir} --opt build-arg:JAR_LOCATION=${CONTAINER_LOCATION + subDir.get()} --opt build-arg:IMAGE_ENTRYPOINT=\"exec java ${javaArgs.join(" ")} -jar  ${CONTAINER_LOCATION}${containerName}.jar\" --output type=image,name=docker-js-temp.software.r3.com/corda-os-${containerName},push=true --export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-${containerName}-cache --import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-${containerName}-cache\n\n")


            // Task executes the buildctl build command below

            // see

            // buildctl \
            //         --addr {address of the buildkit pod} \
            // build --frontend=dockerfile.v0 \
            //         --local context= {working directory} \
            //         --local dockerfile= {location of the dockerfile} \
            //         --opt build-arg:BASE_IMAGE= {base image of the worker, should be worker-cli for os-plugins and azul zulu jdk for everythign else} \
            //         --opt build-arg:BUILD_PATH={location of a folder with fat jars} \
            //         --opt build-arg:JAR_LOCATION={where to save said fat jars on an image}}\
            //         --opt build-arg:IMAGE_ENTRYPOINT={entrypoint: exec of the fat jar with some argument} \
            //         --output type=image,name=docker-js-temp.software.r3.com/IMAGE_NAME:IMAGE_TAG,push=true \
            //         --export-cache type=registry,ref=docker-js-temp.software.r3.com/IMAGE_NAME:IMAGE_TAG-cache \
            //         --import-cache type=registry,ref=docker-js-temp.software.r3.com/IMAGE_NAME:IMAGE_TAG-cache
            commandLine 'bash', '-c', "buildctl --addr tcp://localhost:3476 build --frontend=dockerfile.v0 --local context=/ --local dockerfile=${project.rootDir.toString() + "/docker"} --opt build-arg:BASE_IMAGE=${imageName} --opt build-arg:BUILD_PATH=${containerizationDir} --opt build-arg:JAR_LOCATION=${CONTAINER_LOCATION + subDir.get()} --opt build-arg:IMAGE_ENTRYPOINT=\"exec java ${javaArgs.join(" ")} -jar  ${CONTAINER_LOCATION}${containerName}.jar\" --output type=docker,name=docker-js-temp.software.r3.com/corda-os-${containerName},push=true  | docker load"


    }

    //Copy fat jars to tmp/publishBuildkitImage, runs before the command execution

    static class CopyFiles extends DefaultTask {

        @Internal
        final Property<Boolean> useShortName
        @Internal
        final ConfigurableFileCollection sourceFiles
        @Internal
        final ConfigurableFileCollection extraSourceFiles
        @Internal
        final Path dir

        @Inject
        CopyFiles(ConfigurableFileCollection sourceFiles, ConfigurableFileCollection extraSourceFiles, Property<Boolean> useShortName, Path dir) {

            this.useShortName = useShortName
            this.sourceFiles = sourceFiles
            this.extraSourceFiles = extraSourceFiles
            this.dir = dir

            def containerizationDir = dir

            if (!(Files.exists(containerizationDir))) {
                logger.quiet("Created containerization dir")
                Files.createDirectories(containerizationDir)
            }

            sourceFiles.forEach{
                def jarName = useShortName
                        ? it.name.replace("corda-", "").replace("-${project.version}", "")
                        : it.name
                logger.quiet("\nCopying file ${it.name} to containerization directory\n")
                Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}/$jarName"), StandardCopyOption.REPLACE_EXISTING)
            }

            //for os-plugins, requires config jars

            extraSourceFiles.forEach{
                def jarName = useShortName
                        ? it.name.replace("corda-", "").replace("-${project.version}", "")
                        : it.name
                Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}/$jarName"), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }





}