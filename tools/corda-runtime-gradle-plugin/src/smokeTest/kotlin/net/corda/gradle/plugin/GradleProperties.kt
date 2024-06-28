package net.corda.gradle.plugin

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

data class GradleProperties(
    val artifactoryContextUrl: String = getSystemPropertyOrThrow("artifactoryContextUrl"),
    val cordaApiVersion: String = getSystemPropertyOrThrow("cordaApiVersion"),
    val cordaGradlePluginsVersion: String = getSystemPropertyOrThrow("cordaGradlePluginsVersion"),

    val platformVersion: String = "999",
    val workflowsModule: String = "workflows",
    // TODO: add other properties to have configurable plugin build.gradle
) {
    companion object {
        private fun getSystemPropertyOrThrow(name: String): String {
            return System.getProperty(name) ?: throw IllegalArgumentException("System property $name is not set")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun toKeyValues(): List<String> {
        return this::class.declaredMemberProperties.map {
            val prop = it as KProperty1<GradleProperties, String>
            "-P${prop.name}=${prop.get(this)}"
        }
    }
}
