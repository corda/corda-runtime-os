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
    val cordaRpcUser: Property<String> = objects.property(String::class.java).convention("admin")

    @get:Input
    val cordaRpcPasswd: Property<String> = objects.property(String::class.java).convention("admin")

    @get:Input
    val cordaRuntimePluginWorkspaceDir: Property<String> = objects.property(String::class.java).convention("workspace")

    @get:Input
    val combinedWorkerVersion: Property<String> = objects.property(String::class.java).convention("5.2.0.0")

    @get:Input
    val postgresJdbcVersion: Property<String> = objects.property(String::class.java).convention("42.7.1")

    @get:Input
    var cordaDbContainerName: Property<String> = objects.property(String::class.java).convention("cordaPostgres")

    @get:Input
    val cordaBinDir: Property<String> = objects.property(String::class.java)
        .convention(System.getenv("CORDA_BIN") ?: "${System.getProperty("user.home")}/.corda/corda5")

    @get:Input
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