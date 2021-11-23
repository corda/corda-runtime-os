package net.corda.gradle


import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.JibContainer
import com.google.cloud.tools.jib.api.JibContainerBuilder
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.DockerDaemonImage

import javax.inject.Inject
import java.nio.file.StandardCopyOption

import java.nio.file.Files
import java.nio.file.Paths
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
    private String targetRepo="corda-os-docker-dev.software.r3.com/corda-os-${projectName}"
    private def gitTask

    @Inject
    protected abstract ProviderFactory getProviderFactory()

    @Inject
    protected abstract ObjectFactory getObjects()

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    final RegularFileProperty overrideFile = getObjects().fileProperty().convention(project.tasks.appJar.archiveFile)

    @Input
    final Property<String> registryUsername = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_USERNAME")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryUsername"))
                    .orElse(getProviderFactory().systemProperty("corda.artifactory.username"))
            )

    @Input
    final Property<String> registryPassword = getObjects().property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_PASSWORD")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryPassword"))
                    .orElse(getProviderFactory().systemProperty("corda.artifactory.password"))
            )

    @Input
    final Property<Boolean> remotePublish =
            getObjects().property(Boolean).convention(false)

    @Input
    final Property<Boolean> releaseCandidate =
            getObjects().property(Boolean).convention(false)
    @Input
    final Property<String> baseImageName =
            getObjects().property(String).convention('azul/zulu-openjdk-alpine')

    @Input
    final Property<String> baseImageTag =
            getObjects().property(String).convention('11')

    @Input
    final ListProperty<String> arguments =
            getObjects().listProperty(String)

    @Input
    final Property<String> targetImageTag =
            getObjects().property(String).convention('latest')

    DeployableContainerBuilder() {
        description = 'Creates a new "corda-dev" image with the file specified in "overrideFilePath".'
        group = 'publishing'
        gitTask = project.tasks.register("gitVersion", GetGitRevision.class)
        super.dependsOn(gitTask)
    }

    @TaskAction
    def updateImage() {

        String gitRevision = gitTask.flatMap { it.revision }.get()
        String jarLocation = "${buildDir}/tmp/containerization/${projectName}.jar"
        Files.createDirectories(Paths.get("${buildDir}/tmp/containerization/"))
        Files.copy(Paths.get(overrideFile.getAsFile().get().getPath()), Paths.get(jarLocation), StandardCopyOption.REPLACE_EXISTING)

        RegistryImage baseImage = RegistryImage.named("${baseImageName.get()}:${baseImageTag.get()}")

        JibContainerBuilder builder = Jib.from(baseImage)
                .setCreationTime(Instant.now())
                .addLayer(Arrays.asList(Paths.get(jarLocation)), AbsoluteUnixPath.get(CONTAINER_LOCATION))

        File projectKafkaFile = new File("${projectDir}/$KAFKA_PROPERTIES")
        List<String> javaArgs = new ArrayList<>(arguments.get())

        // copy kafka file to container if file exists and pass as java arguments
        if (new File("${projectDir}/" + KAFKA_PROPERTIES).exists()) {
            logger.quiet("Kafka file found copying ${projectDir}$KAFKA_PROPERTIES to " + CONTAINER_LOCATION + " inside container")
            builder.addLayer(Arrays.asList(Paths.get(projectKafkaFile.getPath())), AbsoluteUnixPath.get(CONTAINER_LOCATION))
            javaArgs.addAll("--kafka", KAFKA_FILE_LOCATION)
        }

        builder.setProgramArguments(javaArgs)

        builder.setEntrypoint("java", "-jar", CONTAINER_LOCATION + projectName +".jar")

        logger.quiet("Publishing '${targetRepo}:${targetImageTag.get()}' and '${targetRepo}:${version}'" +
                " ${remotePublish.get() ? "to remote artifactory" : "to local docker daemon"} with '${projectName}.jar', from base '${baseImageName.get()}:${targetImageTag.get()}'")

        if (releaseCandidate.get()) {
            targetRepo = "corda-os-docker.software.r3.com/corda-os-${projectName}"
        }
        if (!remotePublish.get()) {
            tagContainerForLocal(builder, targetImageTag.get())
            tagContainerForLocal(builder, version)
            tagContainerForLocal(builder, gitRevision)
        } else {
            tagContainerForRemote(builder, targetImageTag.get())
            tagContainerForRemote(builder, version)
            tagContainerForRemote(builder, gitRevision)
        }
    }

    private JibContainer tagContainerForLocal(JibContainerBuilder builder, String tag) {
        builder.containerize(
                Containerizer.to(DockerDaemonImage.named("${targetRepo}:${tag}"))
        )
    }

    private JibContainer tagContainerForRemote(JibContainerBuilder builder, String tag) {
        builder.containerize(
                Containerizer.to(RegistryImage.named("${targetRepo}:${tag}")
                        .addCredential(registryUsername.get(), registryPassword.get())))
    }

    static class GetGitRevision extends Exec {
        @Internal
        final Property<String> revision

        @Inject
        GetGitRevision(ObjectFactory objects, ProviderFactory providers) {
            executable 'git'
            args 'rev-parse', '--verify', '--short', 'HEAD'
            standardOutput = new ByteArrayOutputStream()
            revision = objects.property(String).value(
                    providers.provider { standardOutput.toString() }
            )
        }
    }
}

