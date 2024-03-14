package net.corda.gradle.plugin.configuration

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import javax.inject.Inject

// Gradle extension config in here
open class PluginConfiguration @Inject constructor(objects: ObjectFactory) {

    @get:Input
    val cordaClusterURL: Property<String> = objects.property(String::class.java).convention("https://localhost:8888")

    @get:Input
    val cordaRestUser: Property<String> = objects.property(String::class.java).convention("admin")

    @get:Input
    val cordaRestPasswd: Property<String> = objects.property(String::class.java).convention("admin")

    @get:Input
    val cordaRuntimePluginWorkspaceDir: Property<String> = objects.property(String::class.java).convention("workspace")

    @get:Input
    val composeFilePath: Property<String> = objects.property(String::class.java).convention("config/combined-worker-compose.yml")

    @get:Input
    val composeNetworkName: Property<String> = objects.property(String::class.java).convention("corda-cluster")

    @get:Input
    val notaryVersion: Property<String> = objects.property(String::class.java).convention("5.2.0.0")

    @get:Input
    val cordaBinDir: Property<String> = objects.property(String::class.java)
        .convention(System.getenv("CORDA_BIN") ?: "${System.getProperty("user.home")}/.corda/corda5")

    @get:Input
    @Deprecated("Corda CLI is not required in Gradle plugin configuration")
    val cordaCliBinDir: Property<String> = objects.property(String::class.java)
        .convention(System.getenv("CORDA_CLI") ?: "${System.getProperty("user.home")}/.corda/cli")

    @get:Input
    val artifactoryUsername: Property<String> = objects.property(String::class.java).convention("")

    @get:Input
    val artifactoryPassword: Property<String> = objects.property(String::class.java).convention("")

    @get:Input
    val notaryCpiName: Property<String> = objects.property(String::class.java).convention("NotaryServer")

    @get:Input
    val corDappCpiName: Property<String> = objects.property(String::class.java).convention("MyCorDapp")

    @get:Input
    val cpiUploadTimeout: Property<String> = objects.property(String::class.java).convention("10000")

    @get:Input
    val vnodeRegistrationTimeout: Property<String> = objects.property(String::class.java).convention("30000")

    @get:Input
    val cordaProcessorTimeout: Property<String> = objects.property(String::class.java).convention("-1")

    @get:Input
    val workflowsModuleName: Property<String> = objects.property(String::class.java).convention("workflows")

    @get:Input
    val networkConfigFile: Property<String> = objects.property(String::class.java).convention("config/static-network-config.json")

    @get:Input
    val r3RootCertFile: Property<String> = objects.property(String::class.java).convention("config/r3-ca-key.pem")

    @get:Input
    val skipTestsDuringBuildCpis: Property<String> = objects.property(String::class.java).convention("false")
}