package net.corda.gradle


import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import com.google.cloud.tools.jib.api.*
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath

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
class DeployableContainerBuilder extends DefaultTask {

    private static final String CONTAINER_LOCATION = "/opt/override/"
    private static final String KAFKA_PROPERTIES = "kafka.properties"
    private static final String KAFKA_FILE_LOCATION = CONTAINER_LOCATION + KAFKA_PROPERTIES
    private String defaultUsername = System.getenv("CORDA_ARTIFACTORY_USERNAME") ?: project.findProperty('cordaArtifactoryUsername') ?: System.getProperty('corda.artifactory.username')
    private String defaultPassword = System.getenv("CORDA_ARTIFACTORY_PASSWORD") ?: project.findProperty('cordaArtifactoryPassword') ?: System.getProperty('corda.artifactory.password')

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    final RegularFileProperty overrideFile = project.objects.fileProperty().convention(project.tasks.appJar.archiveFile)

    @Input
    final Property<String> registryUsername = project.objects.property(String).convention(defaultUsername)

    @Input
    final Property<String> registryPassword = project.objects.property(String).convention(defaultPassword)

    @Input
    final Property<Boolean> remotePublish =
            project.objects.property(Boolean).convention(false)

    @Input
    final Property<String> baseImageName =
            project.objects.property(String).convention('azul/zulu-openjdk-alpine')

    @Input
    final Property<String> baseImageTag =
            project.objects.property(String).convention('11')

    @Input
    final ListProperty<String> arguments =
            project.objects.listProperty(String)

    @Input
    final Property<String> targetImageName =
            project.objects.property(String).convention("engineering-docker.software.r3.com/corda-dev/${project.name}")

    @Input
    final Property<String> targetImageTag =
            project.objects.property(String).convention('latest')

    DeployableContainerBuilder() {
        description = 'Creates a new "corda-dev" image with the file specified in "overrideFilePath".'
        group = 'publishing'
    }

    @TaskAction
    def updateImage() {

        String overrideFilePath = overrideFile.getAsFile().get().getPath()
        logger.quiet("Publishing '${targetImageName.get()}:${targetImageTag.get()}' ${remotePublish.get() ? "remotely" : "locally"} with '$overrideFilePath', from base '${baseImageName.get()}:${targetImageTag.get()}'")

        RegistryImage baseImage = RegistryImage.named("${baseImageName.get()}:${baseImageTag.get()}")

        JibContainerBuilder builder = Jib.from(baseImage)
                .setCreationTime(Instant.now())
                .addLayer(Arrays.asList(Paths.get(overrideFilePath)), AbsoluteUnixPath.get(CONTAINER_LOCATION))

        File projectKafkaFile = new File("${project.getProjectDir()}/$KAFKA_PROPERTIES")
        List<String> javaArgs = new ArrayList<>(arguments.get())

        // if kafka file is present in sub project dir it will be copied to container
        // and "--kafka", "/opt/override/kafka.properties" add as arguments
        if (new File("${project.getProjectDir()}/" + KAFKA_PROPERTIES).exists()) {
            logger.quiet("Kafka file found copying ${project.getProjectDir()}$KAFKA_PROPERTIES to " + CONTAINER_LOCATION + " inside container")
            builder.addLayer(Arrays.asList(Paths.get(projectKafkaFile.getPath())), AbsoluteUnixPath.get(CONTAINER_LOCATION))
            javaArgs.addAll("--kafka", KAFKA_FILE_LOCATION)
        }
        builder.addLayer(Arrays.asList(Paths.get(projectKafkaFile.getPath())), AbsoluteUnixPath.get(CONTAINER_LOCATION))
        if (arguments.isPresent() && !arguments.get().isEmpty()) {
            builder.setProgramArguments(javaArgs)
        }
        builder.setEntrypoint("java", "-jar", CONTAINER_LOCATION + overrideFile.getAsFile().get().getName())

        if (remotePublish.get()) {
            builder.containerize(
                    Containerizer.to(RegistryImage.named("${targetImageName.get()}:${targetImageTag.get()}")
                            .addCredential(registryUsername.get(), registryPassword.get()))
            )
        } else {
            logger.quiet("Property jibRemotePublish is false, publishing locally")
            builder.containerize(
                    Containerizer.to(DockerDaemonImage.named("${targetImageName.get()}:${targetImageTag.get()}"))
            )
        }
    }
}
