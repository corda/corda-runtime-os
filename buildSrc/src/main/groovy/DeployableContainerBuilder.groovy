import com.google.cloud.tools.jib.api.*
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.time.Instant

/**
 * Add custom task which will allow us to publish containerized OSGi deployable
 * Currently this deploys a 'fat jar' to the container and we run 'java - jar *args*' as the entry point.
 * A user may pass further custom arguments using the 'arguments' property when using this task type.
 * If a kafka file is present in the sub project directory it will be copied to container and
 * '"--kafka", "/opt/override/kafka.properties"' also passed to the 'java -Jar' entrypoint as additional arguments.
 * Future iterations will use a more modular layered approach.
 */
abstract class DeployableContainerBuilder extends DefaultTask {

    private static final String CONTAINER_LOCATION = "/opt/override/"
    private static final String KAFKA_PROPERTIES = "kafka.properties"
    private static final String KAFKA_FILE_LOCATION = CONTAINER_LOCATION + KAFKA_PROPERTIES
    private final String projectName = project.name
    private final String projectDir = project.projectDir
    private final String buildDir = project.buildDir
    private final String version = project.version
    private String targetRepo
    private def gitTask
    private def gitLogTask
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
            )

    @Input
    @Optional
    final Property<String> registryPassword = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_PASSWORD")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryPassword"))
                    .orElse(getProviderFactory().systemProperty("corda.artifactory.password"))
            )

    @Input
    final Property<Boolean> remotePublish =
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
            getObjects().property(String).convention('11')

    @Input
    final Property<String> subDir =
            getObjects().property(String).convention('')

    @Input
    final ListProperty<String> arguments =
            getObjects().listProperty(String)

    @Input
    final ListProperty<Task> sourceTasks =
            getObjects().listProperty(Task)

    @Input
    final Property<String> overrideEntryName =
            getObjects().property(String).convention('')

    @Input
    final Property<String> overrideContainerName =
            getObjects().property(String).convention('')

    @Input
    final MapProperty<String, String> environment =
            getObjects().mapProperty(String, String).empty()

    DeployableContainerBuilder() {
        description = 'Creates a new "corda-dev" image with the file specified in "overrideFilePath".'
        group = 'publishing'

        gitTask = project.tasks.register("gitVersion", GetLatestGitRevision.class)
        super.dependsOn(gitTask)

        gitLogTask = project.tasks.register("gitMessageTask", getLatestGitCommitMessage.class)
        super.dependsOn(gitLogTask)

        if (System.getenv("RELEASE_TYPE")?.trim()) {
            releaseType = System.getenv("RELEASE_TYPE")
            logger.quiet("Using Release Type : '${releaseType}")
        }
    }

    @TaskAction
    def updateImage() {
        def outputFiles = sourceTasks.get().collect{ it -> it.getOutputs().files.files }.flatten() as List<File>
        def buildBaseDir = temporaryDir.toPath();
        def containerizationDir = Paths.get("$buildBaseDir/containerization/");

        String gitRevision = gitTask.flatMap { it.revision }.get()
        def jiraTicket = hasJiraTicket()
        def timeStamp =  new SimpleDateFormat("ddMMyy").format(new Date())

        if (!(new File(containerizationDir.toString())).exists()) {
            logger.lifecycle("Created containerization dir")
            Files.createDirectories(containerizationDir)
        }

        outputFiles.forEach{
            def jarName = useShortName
                ? it.name.replace("corda-", "").replace("-${project.version}", "")
                : it.name
            Files.copy(Paths.get(it.path), Paths.get("${containerizationDir.toString()}/$jarName"), StandardCopyOption.REPLACE_EXISTING)
        }

        JibContainerBuilder builder = null

        if (useDaemon.get()) { // local use case
            logger.lifecycle("Daemon available")
            def imageName = "${baseImageTag.get().empty ? baseImageName.get() : "${baseImageName.get()}:${baseImageTag.get()}"}"
            //  coerce jib to get locally built images from the docker daemon Without this it trys to `docker inspect` remote images in another thread but cant catch exception
            builder = imageName.endsWith("-local") ? Jib.from(DockerDaemonImage.named(imageName)) : Jib.from(imageName)
        } else {  // CI use case
            logger.lifecycle("No daemon available")
            def baseImage = RegistryImage.named("${baseImageName.get()}:${baseImageTag.get()}")
            if ((registryUsername.get() != null && !registryUsername.get().isEmpty()) && baseImageName.get().contains("software.r3.com")) {
                logger.lifecycle("Add credential to image")
                baseImage.addCredential(registryUsername.get(), registryPassword.get())
            }
            builder = Jib.from(baseImage)
        }
        // If there is no tag for the image - we can't use RegistryImage.named
        builder.setCreationTime(Instant.now())
               .addLayer(
                        containerizationDir.toFile().listFiles().collect { it.toPath() },
                        AbsoluteUnixPath.get(CONTAINER_LOCATION + subDir.get())
                )

        File projectKafkaFile = new File("${projectDir}/$KAFKA_PROPERTIES")
        List<String> javaArgs = new ArrayList<String>(arguments.get())

        javaArgs.add("-Dlog4j.configurationFile=\${LOG4J_CONFIG_FILE}")

        // copy kafka file to container if file exists and pass as java arguments
        if (new File("${projectDir}/" + KAFKA_PROPERTIES).exists()) {
            logger.quiet("Kafka file found copying ${projectDir}$KAFKA_PROPERTIES to " + CONTAINER_LOCATION + " inside container")
            builder.addLayer(Arrays.asList(Paths.get(projectKafkaFile.getPath())), AbsoluteUnixPath.get(CONTAINER_LOCATION))
            javaArgs.addAll("--kafka", KAFKA_FILE_LOCATION)
        }

        if (setEntry.get()) {
            def entryName = overrideEntryName.get().empty ? projectName : overrideEntryName.get()
            builder.setEntrypoint(
                    "/bin/sh",
                    "-c",
                    "exec java -Dlog4j.configurationFile=\${LOG4J_CONFIG_FILE} ${javaArgs.join(" ")} -jar " +
                            CONTAINER_LOCATION + entryName + ".jar \$@",
                    "\$@"
            )
        }
        if (!environment.get().empty) {
            environment.get().each {String key, String value ->
                logger.lifecycle("Adding Env var $key with value $value")
                builder.addEnvironmentVariable(key, value)
            }
        }
        builder.addEnvironmentVariable('LOG4J_CONFIG_FILE', 'log4j2-console.xml')

        def containerName = overrideContainerName.get().empty ? projectName : overrideContainerName.get()

        if (preTest.get()) {
            targetRepo = "corda-os-docker-pre-test.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "preTest-"+version)
            tagContainer(builder, "preTest-"+gitRevision)
        } else if (releaseType == 'RC' || releaseType == 'GA') {
            targetRepo = "corda-os-docker-stable.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "latest")
            tagContainer(builder, version)
        } else if (releaseType == 'BETA' && !nightlyBuild.get()) {
            targetRepo = "corda-os-docker-unstable.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "unstable")
            gitAndVersionTag(builder, gitRevision)
        } else if (releaseType == 'ALPHA' && !nightlyBuild.get()) {
            targetRepo = "corda-os-docker-dev.software.r3.com/corda-os-${containerName}"
            gitAndVersionTag(builder, gitRevision)
        } else if (releaseType == 'BETA' && nightlyBuild.get()){
            targetRepo = "corda-os-docker-nightly.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "nightly")
            tagContainer(builder, "nightly" + "-" + timeStamp)
        } else if (releaseType == 'ALPHA' && nightlyBuild.get()) {
            targetRepo = "corda-os-docker-nightly.software.r3.com/corda-os-${containerName}"
            if (!jiraTicket.isEmpty()) {
                tagContainer(builder, "nightly-" + jiraTicket)
                tagContainer(builder, "nightly" + "-" + jiraTicket + "-" + timeStamp)
                tagContainer(builder, "nightly" + "-" + jiraTicket + "-" + gitRevision)
            }else{
                gitAndVersionTag(builder, "nightly-" + version)
                gitAndVersionTag(builder, "nightly-" + gitRevision)
            }
        } else{
            targetRepo = "corda-os-docker-dev.software.r3.com/corda-os-${containerName}"
            tagContainer(builder, "latest-local")
            gitAndVersionTag(builder, gitRevision)
        }
    }

    private void gitAndVersionTag(JibContainerBuilder builder, String gitRevision) {
        tagContainer(builder, version )
        tagContainer(builder, gitRevision)
    }

    /**
     *  Publish images either to local docker daemon or to the remote repository depending on the
     *  value of remotePublish, CI jobs set this to true by default
     */
    private JibContainer tagContainer(JibContainerBuilder builder, String tag) {
        if (remotePublish.get()) {
            builder.containerize(
                    Containerizer.to(RegistryImage.named("${targetRepo}:${tag}")
                            .addCredential(registryUsername.get(), registryPassword.get())).setAlwaysCacheBaseImage(true))
        } else {
            builder.containerize(
                    Containerizer.to(DockerDaemonImage.named("${targetRepo}:${tag}")).setAlwaysCacheBaseImage(true)
            )
        }

        logger.quiet("Publishing '${targetRepo}:${tag}' ${remotePublish.get() ? "to remote artifactory" : "to local docker daemon"} with jar from '${projectName}', from base '${baseImageName.get()}:${baseImageTag.get()}'")
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
        if (JiraTicket != null) {
            return JiraTicket
        } else {
            return ""
        }
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

