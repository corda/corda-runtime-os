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
    val combinedWorkerVersion: Property<String> = objects.property(String::class.java).convention("5.0.1.0")

    @get:Input
    val postgresJdbcVersion: Property<String> = objects.property(String::class.java).convention("42.4.3")

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

}