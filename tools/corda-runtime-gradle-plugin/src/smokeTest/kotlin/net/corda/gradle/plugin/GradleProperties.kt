package net.corda.gradle.plugin

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

data class GradleProperties(
    val artifactoryContextUrl: String = getSystemPropertyOrThrow("artifactoryContextUrl"),
    val cordaApiVersion: String = getSystemPropertyOrThrow("cordaApiVersion"),
    val cordaGradlePluginsVersion: String = getSystemPropertyOrThrow("cordaGradlePluginsVersion"),

    val platformVersion: String = "999",
    val workflowsModule: String = "workflows",

    val cordaClusterURL: String = "$targetUrl",
    val cordaRestUser: String = USER,
    val cordaRestPasswd: String = PASSWORD,
    val notaryVersion: String = CORDA_RUNTIME_VERSION_STABLE,
    val runtimeVersion: String = CORDA_RUNTIME_VERSION_STABLE, // Applies only to start/stop tasks tests
    val composeFilePath: String = "config/combined-worker-compose.yml",
    val networkConfigFile: String,
) {
    companion object {
        private fun getSystemPropertyOrThrow(name: String): String {
            return System.getProperty(name) ?: throw IllegalArgumentException("System property $name is not set")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun toGradleCmdArgs(): List<String> {
        return this::class.declaredMemberProperties.map {
            val prop = it as KProperty1<GradleProperties, String>
            "-P${prop.name}=${prop.get(this)}"
        }
    }
}
