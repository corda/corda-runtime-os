package net.corda.gradle


import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import com.google.cloud.tools.jib.api.*
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath

import javax.inject.Inject
import java.nio.file.StandardCopyOption;


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
    private String gitVersion = "git rev-parse --verify --short HEAD".execute().text
    private String targetRepo="engineering-docker-dev.software.r3.com/corda-${project.name}"

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    final RegularFileProperty overrideFile = project.objects.fileProperty().convention(project.tasks.appJar.archiveFile)

    @Input
    final Property<String> registryUsername = project.objects.property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_USERNAME")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryUsername"))
                    .orElse(getProviderFactory().gradleProperty("corda.artifactory.username"))
            )

    @Input
    final Property<String> registryPassword = project.objects.property(String).
            convention(getProviderFactory().environmentVariable("CORDA_ARTIFACTORY_PASSWORD")
                    .orElse(getProviderFactory().gradleProperty("cordaArtifactoryPassword"))
                    .orElse(getProviderFactory().gradleProperty("corda.artifactory.password"))
            )

    @Input
    final Property<Boolean> remotePublish =
            project.objects.property(Boolean).convention(false)

    @Input
    final Property<Boolean> releaseCandidate =
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
    final Property<String> targetImageTag =
            project.objects.property(String).convention('latest')

    DeployableContainerBuilder() {
        description = 'Creates a new "corda-dev" image with the file specified in "overrideFilePath".'
        group = 'publishing'
    }

    @TaskAction
    def updateImage() {

        println("gitVersion ${gitVersion}")
        println("registryPassword ${registryPassword.get()}")

        String jarLocation = "${project.buildDir}/tmp/containerization/${project.name}.jar"
        Files.createDirectories(Paths.get("${project.buildDir}/tmp/containerization/"))
        Files.copy(Paths.get(overrideFile.getAsFile().get().getPath()), Paths.get(jarLocation), StandardCopyOption.REPLACE_EXISTING)

        RegistryImage baseImage = RegistryImage.named("${baseImageName.get()}:${baseImageTag.get()}")

        JibContainerBuilder builder = Jib.from(baseImage)
                .setCreationTime(Instant.now())
                .addLayer(Arrays.asList(Paths.get(jarLocation)), AbsoluteUnixPath.get(CONTAINER_LOCATION))

        File projectKafkaFile = new File("${project.getProjectDir()}/$KAFKA_PROPERTIES")
        List<String> javaArgs = new ArrayList<>(arguments.get())

        // copy kafka file to container if file exists and pass as java arguments
        if (new File("${project.getProjectDir()}/" + KAFKA_PROPERTIES).exists()) {
            logger.quiet("Kafka file found copying ${project.getProjectDir()}$KAFKA_PROPERTIES to " + CONTAINER_LOCATION + " inside container")
            builder.addLayer(Arrays.asList(Paths.get(projectKafkaFile.getPath())), AbsoluteUnixPath.get(CONTAINER_LOCATION))
            javaArgs.addAll("--kafka", KAFKA_FILE_LOCATION)
        }

        if (arguments.isPresent() && !arguments.get().isEmpty()) {
            builder.setProgramArguments(javaArgs)
        }
        builder.setEntrypoint("java", "-jar", CONTAINER_LOCATION + project.name+".jar")

        logger.quiet("Publishing '${targetRepo}:${targetImageTag.get()}' and '${targetRepo}:${project.version}'" +
                " ${remotePublish.get() ? "to remote artifactory" : "to local docker daemon"} with '${project.name}.jar', from base '${baseImageName.get()}:${targetImageTag.get()}'")

        if (releaseCandidate.get()) {
            targetRepo = "engineering-docker-release.software.r3.com/corda-${project.name}"
        }
        if (!remotePublish.get()) {
            tagContainerForLocal(builder, targetImageTag.get())
            tagContainerForLocal(builder, project.version)
            tagContainerForLocal(builder, gitVersion)
        } else {
            tagContainerForRemote(builder, targetImageTag.get())
            tagContainerForRemote(builder, project.version)
            tagContainerForRemote(builder, gitVersion)
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
}
