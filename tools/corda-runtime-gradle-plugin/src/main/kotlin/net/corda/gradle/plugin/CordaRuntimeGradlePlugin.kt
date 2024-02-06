package net.corda.gradle.plugin

import net.corda.gradle.plugin.configuration.PluginConfiguration
import net.corda.gradle.plugin.cordalifecycle.createCordaLifeCycleTasks
import net.corda.gradle.plugin.cordalifecycle.createPluginEnvSetupTasks
import net.corda.gradle.plugin.cordapp.createCordappTasks
import net.corda.gradle.plugin.network.createNetworkTasks
import org.gradle.api.Plugin
import org.gradle.api.Project
import net.corda.gradle.plugin.queries.createCordaClusterQueryTasks

const val CONFIG_BLOCK_NAME = "cordaRuntimeGradlePlugin"

class CordaRuntimeGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val projectConfig = project.extensions.create(CONFIG_BLOCK_NAME, PluginConfiguration::class.java)
        createPluginEnvSetupTasks(project, projectConfig)
        createCordaLifeCycleTasks(project, projectConfig)
        createCordappTasks(project, projectConfig)
        createNetworkTasks(project, projectConfig)
        createCordaClusterQueryTasks(project, projectConfig)
    }
}