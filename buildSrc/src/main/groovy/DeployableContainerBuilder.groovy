import com.google.cloud.tools.jib.api.*
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.buildplan.Platform
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import static org.gradle.api.tasks.PathSensitivity.RELATIVE

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.time.Instant

/**
 * Add custom task which will allow us to publish containerized OSGi deployable
 * Currently this deploys a 'fat jar' to the container and we run 'java - jar *args*' as the entry point.
 * A user may pass further custom arguments using the 'arguments' property when using this task type.
 * If a kafka file is present in the sub project directory it will be copied to container and
 */
abstract class DeployableContainerBuilder extends DefaultTask {
    private static final String CONTAINER_LOCATION = "/opt/override/"
    private static final String JDBC_DRIVER_LOCATION = "/opt/jdbc-driver/"
    private static final String JENKINS_JOB_URL_KEY = "JENKINS_URL"
    private static final String JENKINS_GIT_BRANCH_KEY = "GIT_BRANCH"
    private static final String JENKINS_CHANGE_BRANCH_KEY = "CHANGE_BRANCH"
    private final String projectName = project.name
    private final String version = project.version
    private final String cordaProductVersion = project.cordaProductVersion
    private String targetRepo
    private def gitLogTask
    private def gitBranchTask
    private def gitRemoteTask
    private def gitRevisionTask
    private def gitShortRevisionTask
    private def releaseType

    @Inject
    protected abstract ProviderFactory getProviderFactory()

    @Inject
    protected abstract ObjectFactory getObjects()

    @Input
    @Optional
    final Property<String> registryUsername = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_USERNAME")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryUsername"))
                    .orElse(getProviderFactory().systemProperty("corda.artifactory.username"))
                    .orElse("")
            )

    @Input
    @Optional
    final Property<String> registryPassword = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_PASSWORD")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryPassword"))
                    .orElse(getProviderFactory().systemProperty("corda.artifactory.password"))
                    .orElse("")
            )

    @Input
    @Optional
    final Property<String> dockerHubUsername = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("DOCKER_HUB_USERNAME")
                    .orElse(getProviderFactory().gradleProperty("dockerHubUsername"))
                    .orElse(getProviderFactory().systemProperty("docker.hub.username"))
                    .orElse("")
            )

    @Input
    @Optional
    final Property<String> dockerHubPassword = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("DOCKER_HUB_PASSWORD")
                    .orElse(getProviderFactory().gradleProperty("dockerHubPassword"))
                    .orElse(getProviderFactory().systemProperty("docker.hub.password"))
                    .orElse("")
            )

    @Input
    final Property<Boolean> remotePublish =
            getObjects().property(Boolean).convention(false)

    @Input
    final Property<Boolean> dockerHubPublish =
            getObjects().property(Boolean).convention(false)

    @Input
    final Property<Boolean> setEntry =
            getObjects().property(Boolean).convention(true)

    @Input
    final Property<Boolean> useShortName =
            getObjects().property(Boolean).convention(false)

    @Input
    final Property<Boolean> releaseCandidate =
            getObjects().property(Boolean).convention(false)

    @Input
    final Property<Boolean> nightlyBuild =
            getObjects().property(Boolean).convention(false)

    @Input
    final Property<Boolean> preTest =
            getObjects().property(Boolean).convention(false)

    @Input
    final Property<Boolean> useDaemon =
            getObjects().property(Boolean).convention(true)


    @Input
    final Property<String> baseImageName =
            getObjects().property(String).convention('azul/zulu-openjdk')

    @Input
    final Property<String> baseImageTag =
            getObjects().property(String).convention('17.0.8.1-17.44.53')

    @Input
    final Property<String> subDir =
            getObjects().property(String).convention('')

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
    final Property<String> overrideEntryName =
            getObjects().property(String).convention('')

    @Input
    final Property<String> overrideContainerName =
            getObjects().property(String).convention('')

    @Input
    final MapProperty<String, String> environment =
            getObjects().mapProperty(String, String).empty()

    @Input
    final Property<Boolean> multiArch =
            getObjects().property(Boolean).convention(true)

    @Input
    @Optional
    // Force Target Platform, using format "operatingSystem/architecture"
    final Property<String> targetPlatform =
            getObjects().property(String).convention('')

    DeployableContainerBuilder() {
        description = 'Creates a new "corda-dev" image with the file specified in "overrideFilePath".'
        group = 'publishing'

        gitBranchTask = project.tasks.register("gitBranch", GetGitBranch.class)
        super.dependsOn(gitBranchTask)

        gitRemoteTask = project.tasks.register("gitRemote", GetGitRemoteUrl.class)
        super.dependsOn(gitRemoteTask)

        gitRevisionTask = project.tasks.register("gitRevision", GetGitRevision.class)
        super.dependsOn(gitRevisionTask)

        // TODO: remove once CORE-13474 has been implemented.
        // Several pipelines currently use 'git rev-parse --short' to determine the custom image tag to use when
        // deploying corda and the length of the abbreviated commit hash is determined by local Git configuration, so
        // we can't assume that the default of 7 is used everywhere.
        gitShortRevisionTask = project.tasks.register("gitShortRevision", GetGitShortRevision.class)
        super.dependsOn(gitShortRevisionTask)

        gitLogTask = project.tasks.register("gitMessageTask", getLatestGitCommitMessage.class)
        super.dependsOn(gitLogTask)

        if (System.getenv("RELEASE_TYPE")?.trim()) {
            releaseType = System.getenv("RELEASE_TYPE")
            logger.quiet("Using Release Type : '${releaseType}")
        }
    }

    @TaskAction
    def updateImage() {
        def buildBaseDir = temporaryDir.toPath()
        def containerizationDir = Paths.get("$buildBaseDir/containerization/")
        String tagPrefix = ""
        String gitRevisionShortHash = gitShortRevisionTask.flatMap { it.revision }.get()

        def jiraTicket = hasJiraTicket()
        def timeStamp =  new SimpleDateFormat("ddMMyy").format(new Date())

        if (!(new File(containerizationDir.toString())).exists()) {
            logger.info("Created containerization dir")
            Files.createDirectories(containerizationDir)
        }

        sourceFiles.forEach{
            def jarName = useShortName
                    ? it.name.replace("corda-", "").replace("-${project.version}", "")
                    : it.name
            Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}/$jarName"), StandardCopyOption.REPLACE_EXISTING)
        }

        JibContainerBuilder builder = null

        if (useDaemon.get()) {
            logger.info("Daemon available")
            def imageName = "${baseImageTag.get().empty ? baseImageName.get() : "${baseImageName.get()}:${baseImageTag.get()}"}"
            if (imageName.endsWith("latest-local-${cordaProductVersion}")) {
                logger.info("Resolving base image ${baseImageName.get()}:${baseImageTag.get()} from local Docker daemon")
                builder = Jib.from(DockerDaemonImage.named(imageName))
            } else if (imageName.contains("software.r3.com")) {
                logger.info("Resolving base image ${baseImageName.get()}:${baseImageTag.get()} from internal remote repo")
                builder = setCredentialsOnBaseImage(builder)
            } else {
                logger.info("Resolving base image ${baseImageName.get()}: ${baseImageTag.get()} from remote repo")
                builder = setCredentialsOnBaseImage(builder)
            }
        } else {  // CI use case
            logger.info("No daemon available")
            logger.info("Resolving base image ${baseImageName.get()}: ${baseImageTag.get()} from remote repo")
            builder = setCredentialsOnBaseImage(builder)
        }

        // Add labels (branch, source code url and git commit SHA) to the image
        addImageSourceLabels(builder)

        List<Path> jdbcDrivers = jdbcDriverFiles.collect { it.toPath() }
        if (!jdbcDrivers.empty) {
            builder.addLayer(jdbcDrivers, JDBC_DRIVER_LOCATION)
        }

        List<Path> imageFiles = extraSourceFiles.collect { it.toPath() }
        if (!imageFiles.empty) {
            builder.addLayer(imageFiles, CONTAINER_LOCATION)
        }

        // If there is no tag for the image - we can't use RegistryImage.named
        builder.setCreationTime(Instant.now())
                .addLayer(
                        containerizationDir.toFile().listFiles().collect { it.toPath() },
                        AbsoluteUnixPath.get(CONTAINER_LOCATION + subDir.get())
                )
        List<String> javaArgs = new ArrayList<String>(arguments.get())
        javaArgs.add("-Dlog4j2.debug=\${ENABLE_LOG4J2_DEBUG:-false}")
        javaArgs.add("-Dlog4j.configurationFile=\${LOG4J_CONFIG_FILE}")

        if (setEntry.get()) {
            def entryName = overrideEntryName.get().empty ? projectName : overrideEntryName.get()
            builder.setEntrypoint(
                    "/bin/sh",
                    "-c",
                    "exec java ${javaArgs.join(" ")} -jar " + CONTAINER_LOCATION + entryName + ".jar \"\$@\"",
                    "\"\$@\""
            )
        }
        if (!environment.get().empty) {
            environment.get().each { String key, String value ->
                logger.info("Adding Env var $key with value $value")
                builder.addEnvironmentVariable(key, value)
            }
        }
        builder.addEnvironmentVariable('LOG4J_CONFIG_FILE', 'log4j2-console.xml')
        builder.addEnvironmentVariable('ENABLE_LOG4J2_DEBUG', 'false')
        builder.addEnvironmentVariable('CONSOLE_LOG_LEVEL', 'info')

        if (!targetPlatform.get().empty) {
            logger.quiet("Forcing Jib to use ${targetPlatform.get()} platform")
            String[] osArch = targetPlatform.get().split("/")
            builder.setPlatforms(Set.of(new Platform(osArch[1], osArch[0])))
        } else if (System.getenv().containsKey(JENKINS_JOB_URL_KEY) && multiArch.get()) {
            logger.quiet("${multiArch.get() ? 'Running on CI server - producing arm64 and amd64 images' : 'Running on CI server but multiArch flag set to false - producing amd64 images'}")
            builder.addPlatform("arm64","linux")
        } else if (System.properties['os.arch'] == "aarch64") {
            logger.quiet("Detected arm64 host, switching Jib to produce arm64 images")
            builder.setPlatforms(Set.of(new Platform("arm64", "linux")))
            tagPrefix = "arm64-"
        } else {
            logger.quiet("Detected amd64 host, producing amd64 images")
            // Default JIB configuration no specific action needed
        }

        def containerName = overrideContainerName.get().empty ? projectName : overrideContainerName.get()

        if (dockerHubPublish.get()) {
            targetRepo = "corda/corda-os-${containerName}"
            tagContainer(builder, version)
        } else if (preTest.get()) {
            targetRepo = "corda-os-docker-pre-test.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "preTest-${tagPrefix}"+version)
            tagContainer(builder, "preTest-${tagPrefix}"+gitRevisionShortHash)
        } else if (releaseType == 'RC' || releaseType == 'GA') {
            targetRepo = "corda-os-docker-stable.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "${tagPrefix}latest-${cordaProductVersion}")
            tagContainer(builder, "${tagPrefix}${version}")
        } else if (releaseType == 'BETA' && !nightlyBuild.get()) {
            targetRepo = "corda-os-docker-unstable.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "${tagPrefix}unstable-${cordaProductVersion}")
            gitAndVersionTag(builder, "${tagPrefix}${gitRevisionShortHash}")
        } else if (releaseType == 'ALPHA' && !nightlyBuild.get()) {
            targetRepo = "corda-os-docker-dev.software.r3.com/corda-os-${containerName}"
            gitAndVersionTag(builder, "${tagPrefix}${gitRevisionShortHash}")
        } else if (releaseType == 'BETA' && nightlyBuild.get()){
            targetRepo = "corda-os-docker-nightly.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "${tagPrefix}nightly")
            tagContainer(builder, "${tagPrefix}nightly" + "-" + timeStamp)
        } else if (releaseType == 'ALPHA' && nightlyBuild.get()) {
            targetRepo = "corda-os-docker-nightly.software.r3.com/corda-os-${containerName}"
            if (!jiraTicket.isEmpty()) {
                tagContainer(builder, "${tagPrefix}nightly-" + jiraTicket)
                tagContainer(builder, "${tagPrefix}nightly" + "-" + jiraTicket + "-" + timeStamp)
                tagContainer(builder, "${tagPrefix}nightly" + "-" + jiraTicket + "-" + gitRevisionShortHash)
            }else{
                gitAndVersionTag(builder, "${tagPrefix}nightly-" + version)
                gitAndVersionTag(builder, "${tagPrefix}nightly-" + gitRevisionShortHash)
            }
        } else{
            targetRepo = "corda-os-docker-dev.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "latest-local-${cordaProductVersion}")
            gitAndVersionTag(builder, gitRevisionShortHash)
        }
    }

    /**
     * Sets credentials on the base image used in the Jib container builder.
     * In cases where no valid credentials are provided, the base image will be pulled anonymously.
     *
     * @param builder The Jib container builder to set credentials on.
     * @return The updated Jib container builder with credentials set if applicable.
     */
    private JibContainerBuilder setCredentialsOnBaseImage(JibContainerBuilder builder) {
        def baseImage = RegistryImage.named("${baseImageName.get()}:${baseImageTag.get()}")
        if ((registryUsername.get() != null && !registryUsername.get().isEmpty()) && baseImageName.get().contains("software.r3.com")) {
            logger.info("Authenticating against Artifactory for base image resolution")
            baseImage.addCredential(registryUsername.get(), registryPassword.get())
            builder = Jib.from(baseImage)
        } else if ((dockerHubUsername.get() != null && !dockerHubUsername.get().isEmpty()) &&
                (dockerHubPassword.get() != null && !dockerHubPassword.get().isEmpty())) {
            logger.info("Authenticating against Docker Hub for base image resolution")
            baseImage.addCredential(dockerHubUsername.get(), dockerHubPassword.get())
            builder = Jib.from(baseImage)
        } else {
            logger.info("Pulling base image from Docker Hub anonymously")
            builder = Jib.from(baseImage)
        }
        return builder
    }

    private void addImageSourceLabels(JibContainerBuilder builder) {
        String gitRemote = gitRemoteTask.flatMap { it.url }.get()
        String gitRevision = gitRevisionTask.flatMap { it.revision }.get()

        // Jenkins automatically overrides the branch name when checking out the source code, we can't just use
        // regular git commands when the task is being executed from within CI.
        def gitBranch = ""
        if (System.getenv().containsKey(JENKINS_JOB_URL_KEY)) {
            if (System.getenv().containsKey(JENKINS_CHANGE_BRANCH_KEY)) {
                gitBranch = System.getenv(JENKINS_CHANGE_BRANCH_KEY) // PR build on jenkins
            } else if (System.getenv().containsKey(JENKINS_GIT_BRANCH_KEY)) {
                gitBranch = System.getenv(JENKINS_GIT_BRANCH_KEY)  // branch builds on jenkins
            }
        } else {
            gitBranch = gitBranchTask.flatMap { it.branch }.get()
        }

        logger.quiet("GitRemote: '{}', GitBranch: '{}', GitCommit: '{}'", gitRemote, gitBranch, gitRevision)
        builder.addLabel("com.r3.corda.git.branch", gitBranch)
        builder.addLabel("org.opencontainers.image.source", gitRemote)
        builder.addLabel("org.opencontainers.image.revision", gitRevision)
    }

    private void gitAndVersionTag(JibContainerBuilder builder, String gitRevision) {
        tagContainer(builder, version )
        tagContainer(builder, gitRevision)
    }

    /**
     *  Publish images either to local docker daemon or to the remote repository depending on the
     *  value of remotePublish, CI jobs set this to true by default
     */
    private void tagContainer(JibContainerBuilder builder, String tag) {
        if (remotePublish.get()) {
            builder.containerize(
                    Containerizer.to(RegistryImage.named("${targetRepo}:${tag}")
                            .addCredential(registryUsername.get(), registryPassword.get())).setAlwaysCacheBaseImage(true))
        } else if (dockerHubPublish.get()){
            builder.containerize(
                    Containerizer.to(RegistryImage.named("${targetRepo}:${tag}")
                            .addCredential(dockerHubUsername.get(), dockerHubPassword.get())).setAlwaysCacheBaseImage(true))
        } else {
            builder.containerize(
                    Containerizer.to(DockerDaemonImage.named("${targetRepo}:${tag}")).setAlwaysCacheBaseImage(true)
            )
        }

        logger.quiet("Publishing '${targetRepo}:${tag}' ${(remotePublish.get() || dockerHubPublish.get()) ? "${dockerHubPublish.get() ? 'to docker hub' : 'to remote artifactory'}" : "to local docker daemon"} with jar from '${projectName}', from base '${baseImageName.get()}:${baseImageTag.get()}'")
    }

    /**
     * Helper method to retrieve the Jira ticket from last git commit if it exists
     * Returns: the Jira ID, empty String otherwise, used in tagging of nightly Alphas
     */
    def hasJiraTicket() {
        def JiraTicket
        String gitLogMessage = gitLogTask.flatMap { it.message }.get()
        if (gitLogMessage =~ /(^(CORDA|EG|ENT|INFRA|CORE)-\d+|^NOTICK)/) {
            JiraTicket = (gitLogMessage =~ /(^(CORDA|EG|ENT|INFRA|CORE)-\d+|^NOTICK)/)[0][0]
        }
        return (JiraTicket != null) ? JiraTicket : ""
    }

    /**
     * Helper task to retrieve the latest full git hash
     */
    static class GetGitRevision extends Exec {
        @Internal
        final Property<String> revision

        @Inject
        GetGitRevision(ObjectFactory objects, ProviderFactory providers) {
            executable 'git'
            args 'rev-parse', '--verify', 'HEAD'
            standardOutput = new ByteArrayOutputStream()
            revision = objects.property(String).value(
                    providers.provider { standardOutput.toString().trim() }
            )
        }
    }

    /**
     * Helper task to retrieve the latest full git hash
     */
    static class GetGitShortRevision extends Exec {
        @Internal
        final Property<String> revision

        @Inject
        GetGitShortRevision(ObjectFactory objects, ProviderFactory providers) {
            executable 'git'
            args 'rev-parse', '--verify', '--short', 'HEAD'
            standardOutput = new ByteArrayOutputStream()
            revision = objects.property(String).value(
                    providers.provider { standardOutput.toString().trim() }
            )
        }
    }

    /**
     * Helper task to retrieve the git branch
     */
    static class GetGitBranch extends Exec {
        @Internal
        final Property<String> branch

        @Inject
        GetGitBranch(ObjectFactory objects, ProviderFactory providers) {
            executable 'git'
            args 'rev-parse', '--abbrev-ref', 'HEAD'
            standardOutput = new ByteArrayOutputStream()
            branch = objects.property(String).value(
                    providers.provider { standardOutput.toString().trim() }
            )
        }
    }

    /**
     * Helper task to retrieve get the git remote repository
     */
    static class GetGitRemoteUrl extends Exec {
        @Internal
        final Property<String> url

        @Inject
        GetGitRemoteUrl(ObjectFactory objects, ProviderFactory providers) {
            executable 'git'
            args 'remote', 'get-url', 'origin'
            standardOutput = new ByteArrayOutputStream()
            url = objects.property(String).value(
                    providers.provider { standardOutput.toString().trim() }
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
