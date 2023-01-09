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
import org.gradle.api.tasks.Optional
import org.gradle.api.provider.ProviderFactory
import static org.gradle.api.tasks.PathSensitivity.RELATIVE
import javax.inject.Inject
import java.net.Socket
import java.text.SimpleDateFormat
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 *  Task to publish worker images to artifactory using buildkit
 *  https://r3-cev.atlassian.net/wiki/spaces/CB/pages/4063035406/BuildKit
 */
abstract class BuildkitBuild extends Exec {

    private final String projectName = project.name
    private final String version = project.version
    private def gitTask
    private def gitLogTask
    private def releaseType

    @Inject
    protected abstract ObjectFactory getObjects()

    @Inject
    protected abstract ProviderFactory getProviderFactory()

    // Credentials used for authentication to docker and artifactory
    @Input
    @Optional
    final Property<String> registryUsername = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_USERNAME")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryUsername"))
                    .orElse(getProviderFactory().systemProperty("corda.artifactory.username"))
            )

    @Input
    @Optional
    final Property<String> registryPassword = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_PASSWORD")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryPassword"))
                    .orElse(getProviderFactory().systemProperty("corda.artifactory.password"))
            )

    // Property to set images for release 
    @Input
    final Property<Boolean> releaseCandidate = getObjects().property(Boolean).convention(false)

    //Property for nightly builds
    @Input
    final Property<Boolean> nightlyBuild = getObjects().property(Boolean).convention(false)

    // Property for pre-test
    @Input
    final Property<Boolean> preTest = getObjects().property(Boolean).convention(false)

    // Property to set the build tool used to create an image
    // Currently supported tools are docker buildx and native builkit 
    // NOTE: native buidkit requires port forwarding to the aws buildkit cluster
    @Input
    final Property<Boolean> isBuildx = getObjects().property(Boolean).convention(true)

    // Property for loading images into docker
    @Input
    final Property<Boolean> useDockerDaemon = getObjects().property(Boolean).convention(true)

    // The images contain shortened filenames if true
    @Input
    final Property<Boolean> useShortName = getObjects().property(Boolean).convention(false)

    // Property to set the output image's repository
    @Input
    final Property<String> containerRepo = getObjects().property(String).convention('')

    // Property used to set custom image tag
    @Input
    final Property<String> containerTag = getObjects().property(String).convention('')

    // Property used to append custom image tag
    @Input
    final Property<String> appendContainerTag = getObjects().property(String).convention('')

    // Property used to set custom image name
    @Input
    final Property<String> containerName = getObjects().property(String).convention('')

    // Property to set the base image's name
    @Input
    final Property<String> baseImageName = getObjects().property(String).convention('azul/zulu-openjdk')

    // Property to set the base image's tag
    @Input
    final Property<String> baseImageTag = getObjects().property(String).convention('11')

    // Arguments passed to the image entrypoint
    @Input
    final ListProperty<String> arguments = getObjects().listProperty(String)

    // Source Files generated in the build that need to be inside the container
    @PathSensitive(RELATIVE)
    @SkipWhenEmpty
    @InputFiles
    final ConfigurableFileCollection sourceFiles = getObjects().fileCollection()

    // Additional files (plugins for example) generated in the build that need to be inside the container
    @PathSensitive(RELATIVE)
    @InputFiles
    final ConfigurableFileCollection extraSourceFiles = getObjects().fileCollection()

    // JDBC driver files generated in the build that need to be inside the container
    @PathSensitive(RELATIVE)
    @InputFiles
    final ConfigurableFileCollection jdbcDriverFiles = getObjects().fileCollection()

    // Used to create a folder for plugins in tools:plugins task
    @Input
    final Property<String> subDir = getObjects().property(String).convention('')

    // Overrides name of the jar file being executed at the entrypoint
    @Input
    final Property<String> overrideEntryName = getObjects().property(String).convention('')

    // Publishing to dockerhub
    @Input
    final Property<Boolean> dockerHubPublish = getObjects().property(Boolean).convention(false)

    BuildkitBuild() {
        group = 'publishing'

        gitTask = project.tasks.register("gitVersionBuildkit", GetLatestGitRevision.class)
        super.dependsOn(gitTask)

        gitLogTask = project.tasks.register("gitMessageTaskBuildkit", getLatestGitCommitMessage.class)
        super.dependsOn(gitLogTask)

        if (System.getenv("RELEASE_TYPE")?.trim()) {
            releaseType = System.getenv("RELEASE_TYPE")
            logger.quiet("Using Release Type : '${releaseType}")
        }
    }

    @Override
    @TaskAction
    protected void exec() {
        def buildBaseDir = temporaryDir.toPath()
        def containerizationDir = Paths.get("$buildBaseDir/containerization/")
        def driverDir = Paths.get("$buildBaseDir/jdbc-driver/")
        def containerLocation = '/opt/override/'
        def driverLocation = '/opt/jdbc-driver'
        String gitRevision = gitTask.flatMap { it.revision }.get().replace("\n", "")
        def imageRepo = generateDockerTagging(gitRevision)
        List<String> dockerAuth = new ArrayList(['docker-remotes.software.r3.com', 'corda-os-docker.software.r3.com'])

        workingDir project.rootDir

        checkForBuilderContainer()

        if (!(Files.exists(containerizationDir))) {
            logger.quiet("Created containerization dir")
            Files.createDirectories(containerizationDir)
        }

        if (!(Files.exists(driverDir))) {
            logger.quiet("Created jdbc-driver dir")
            Files.createDirectories(driverDir)
        }

        (sourceFiles + extraSourceFiles).forEach {
            if (Files.exists(Paths.get(it.path))) {
                Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}/$it.name"), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        jdbcDriverFiles.forEach {
            if (Files.exists(Paths.get(it.path))) {
                Files.copy(Paths.get(it.path), Paths.get("${driverDir.toString()}/$it.name"), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        executeBuildkitCommand(containerLocation, dockerAuth, imageRepo, containerizationDir, driverDir, driverLocation)
    }

    /**
     * helper method to build and execute the buildkit command
     * @param containerLocation
     * @param dockerAuth list of repositories to log into
     * @param imageRepo list of repositories to publish to
     * @param containerizationDir
     * @param driverDir
     * @param driverLocation
     */
    def executeBuildkitCommand(containerLocation, List<String> dockerAuth, ArrayList imageRepo, containerizationDir, driverDir, driverLocation) {
        List<String> javaArgs = new ArrayList<String>(arguments.get())
        javaArgs.add("-Dlog4j2.debug=\${ENABLE_LOG4J2_DEBUG:-false}")
        javaArgs.add("-Dlog4j.configurationFile=log4j2-console.xml")
        javaArgs.add("-Dpf4j.pluginsDir=${containerLocation + subDir.get()}")

        def baseImageName = "${baseImageTag.get().empty ? baseImageName.get() : "${baseImageName.get()}:${baseImageTag.get()}"}"
        def entryName = overrideEntryName.get().empty ? projectName : overrideEntryName.get()

        for (repo in imageRepo) {
            dockerAuth.add(repo.name)
        }
        
        if ((registryUsername.get() != null && !registryUsername.get().isEmpty()) && baseImageName.contains("software.r3.com")) {
            dockerLogin(dockerAuth)
        }

        String[] baseCommand, opts, commandTail
        List<String> imageNames

        for (repo in imageRepo) {
            imageNames = new ArrayList()
            for (tag in repo.tag) {
                imageNames.add("${repo.name}/corda-os-${containerName.get()}:${tag + appendContainerTag.get()}")
            }

            if (isBuildx.get()) {
                logger.info("\nUsing docker Buildx\n")
                baseCommand = ['docker', 'buildx', "build", "--file ./docker/Dockerfile"]
                opts = ["--build-arg BASE_IMAGE=${baseImageName}",
                        "--build-arg BUILD_PATH=${containerizationDir.toString().replace("${project.rootDir}", ".")}",
                        "--build-arg JAR_LOCATION=${containerLocation + subDir.get()}",
                        "--build-arg JDBC_PATH=${driverDir.toString().replace("${project.rootDir}", ".")}",
                        "--build-arg JDBC_DRIVER_LOCATION=${driverLocation}",
                        "--build-arg IMAGE_ENTRYPOINT=\"exec java ${javaArgs.join(" ")} -jar  ${containerLocation}*${entryName}**.jar\" "]
                commandTail = ["--${useDockerDaemon.get() ? "load" : "push"}",
                               "-t ${imageNames.join(" -t ")}",
                               "--cache-from ${repo.name}/corda-os-${containerName.get()}-cache",
                               "--cache-to type=registry,ref=${repo.name}/corda-os-${containerName.get()}-cache",
                               "."]
            } else {
                logger.info("\nUsing native buildkit client\n")
                baseCommand = ['buildctl', "--addr tcp://localhost:3476", "build", "--frontend=dockerfile.v0", "--local context=/", "--local dockerfile=${project.rootDir.toString() + "/docker"}"]
                opts = ["--opt build-arg:BASE_IMAGE=${baseImageName}",
                        "--opt build-arg:BUILD_PATH=${containerizationDir}",
                        "--opt build-arg:JAR_LOCATION=${containerLocation + subDir.get()}",
                        "--opt build-arg:JDBC_PATH=${driverDir}",
                        "--opt build-arg:JDBC_DRIVER_LOCATION=${driverLocation}",
                        "--opt build-arg:IMAGE_ENTRYPOINT=\"exec java ${javaArgs.join(" ")} -jar  ${containerLocation}*${entryName}**.jar\" "]
                commandTail = ["--output type=${useDockerDaemon.get() ? "docker" : "image"},\\\"name=${imageNames.join(",")}\\\"${useDockerDaemon.get() ? "" : ",push=true"}",
                               "--export-cache type=registry,ref=${repo.name}/corda-os-${containerName.get()}-cache",
                               "--import-cache type=registry,ref=${repo.name}/corda-os-${containerName.get()}-cache${useDockerDaemon.get() ? " | docker load" : ""}"]
            }

            String[] buildkitCommand = baseCommand + opts + commandTail
            logger.info("${buildkitCommand.join('\n')}")
            execShellCommand(buildkitCommand)
        }
    }

    /**
     * Helper method to populate tagging logic
     * @param gitRevision short git commit hash
     * @return imageRepo arraylist of repositories to be published and their tags
     */
    def generateDockerTagging(String gitRevision) {
        def targetRepo, targetTags
        def imageRepo = []
        if (!containerRepo.get().isEmpty()) {
            targetRepo = "${containerRepo.get()}"
            if (!containerTag.get().isEmpty()) {
                targetTags = ["${containerTag.get()}"]
            } else {
                targetTags = ["latest"]
            }
        } else if (dockerHubPublish.get()) {
            targetRepo = "corda"
            targetTags = ["${version}"]
        } else if (preTest.get()) {
            targetRepo = "corda-os-docker-pre-test.software.r3.com"
            targetTags = ["preTest-${version}", "preTest-${gitRevision}"]
        } else if (releaseType == 'RC' || releaseType == 'GA') {
            targetRepo = "corda-os-docker-stable.software.r3.com"
            targetTags = ["latest", "${version}"]
        } else if (releaseType == 'BETA' && !nightlyBuild.get()) {
            targetRepo = "corda-os-docker-unstable.software.r3.com"
            targetTags = ["unstable", "${gitRevision}", "${version}"]
        } else if (releaseType == 'ALPHA' && !nightlyBuild.get()) {
            targetRepo = "corda-os-docker-dev.software.r3.com"
            targetTags = ["${gitRevision}", "${version}"]
        } else if (releaseType == 'BETA' && nightlyBuild.get()) {
            targetRepo = "corda-os-docker-nightly.software.r3.com"
            targetTags = ["nightly", "nightly-${new SimpleDateFormat("ddMMyy").format(new Date())}"]
        } else if (releaseType == 'ALPHA' && nightlyBuild.get()) {
            targetRepo = "corda-os-docker-nightly.software.r3.com"
            targetTags = ["$nightly-${version}", "$nightly-${gitRevision}"]
        } else {
            targetRepo = "corda-os-docker-dev.software.r3.com"
            targetTags = ["latest-local", "${version}", "${gitRevision}"]
        }

        imageRepo.add([name: targetRepo, tag: targetTags])

        return imageRepo
    }

    /**
     * check for buildx docker container driver and create a new one if not present
     */
    def checkForBuilderContainer() {
        if (isBuildx.get()) {
            try {
                String[] cmd = ['docker', 'buildx', 'use', 'container']
                execShellCommand(cmd)
            } catch (GradleException e) {
                logger.info("no buildx container found, creating a fresh container")
                String[] cmd = ['docker', 'buildx', 'create', '--name=container', '--driver=docker-container', '--use', '--bootstrap']
                execShellCommand(cmd)
            }
        } else {
            try {
                (new Socket('127.0.0.1', 3476)).close();
                logger.info("Buildkit daemon found")
            }
            catch (SocketException e) {
                throw new GradleException("No daemon found. Please connect to available buildkit daemon (and port forward it to 3476) and start again")
            }
        }
    }

    /**
     * log into docker repositories in the list
     * @param repositoryList list of repositories to log into
     */
    def dockerLogin(List<String> repositoryList) {
        for (repo in repositoryList) {
            logger.info("Logging into ${repo}")
            String[] cmd = ['docker', 'login', "${repo}", "-u ${registryUsername.get()}", "-p ${registryPassword.get()}"]
            execShellCommand(cmd)
        }
    }

    /**
     * executes shell command
     * @param Command command to be executed
     */
    def execShellCommand(String[] Command) {
        String systemCommand
        String systemPrefix
        if (System.properties['os.name'].toLowerCase().contains('windows')) {
            systemCommand = 'powershell'
            systemPrefix = '/c'
        } else {
            systemCommand = 'bash'
            systemPrefix = '-c'
        }
        logger.info("Executing ${Command.join(" ")}")
        commandLine systemCommand, systemPrefix, Command.join(" ")
        super.exec()
    }

    /**
     * Helper task to retrieve get the latest git hash
     */
    static class GetLatestGitRevision extends Exec {
        @Internal
        final Property<String> revision

        @Inject
        GetLatestGitRevision(ObjectFactory objects, ProviderFactory providers) {
            executable 'git'
            args 'rev-parse', '--verify', '--short', 'HEAD'
            standardOutput = new ByteArrayOutputStream()
            revision = objects.property(String).value(
                    providers.provider { standardOutput.toString() }
            )
        }
    }

    /**
     * Helper task to retrieve the latest git log message
     */
    static class getLatestGitCommitMessage extends Exec {
        @Internal
        final Property<String> message

        @Inject
        getLatestGitCommitMessage(ObjectFactory objects, ProviderFactory providers) {
            executable 'git'
            args 'log', '-1', '--oneline', '--format=%s'

            standardOutput = new ByteArrayOutputStream()
            message = objects.property(String).value(
                    providers.provider { standardOutput.toString() }
            )
        }
    }
}
