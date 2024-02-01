package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.configuration.PluginConfiguration
import net.corda.gradle.plugin.configuration.ProjectContext
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

// Tasks related to querying a running Corda instance

const val CLUSTER_QUERY_GROUP = "corda-runtime-plugin-queries"
const val LIST_VNODES_TASK_NAME = "listVNodes"
const val LIST_CPIS_TASK_NAME = "listCPIs"

/**
 * Creates the gradle helper tasks in the corda-runtime-plugin-queries group
 */
fun createCordaClusterQueryTasks(project: Project, pluginConfig: PluginConfiguration) {
    project.afterEvaluate {
        project.tasks.create(LIST_VNODES_TASK_NAME, ListVNodes::class.java) {
            it.group = CLUSTER_QUERY_GROUP
            it.pluginConfig.set(pluginConfig)
        }

        project.tasks.create(LIST_CPIS_TASK_NAME, ListCPIs::class.java) {
            it.group = CLUSTER_QUERY_GROUP
            it.pluginConfig.set(pluginConfig)
        }
    }
}

open class ListVNodes @Inject constructor(objects: ObjectFactory): DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)
    @TaskAction
    fun listVNodes() {
        val pc = ProjectContext(project, pluginConfig.get())
        QueryTasksImpl(pc).listVNodes()
    }
}

open class ListCPIs @Inject constructor(objects: ObjectFactory): DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)
    @TaskAction
    fun listVNodes() {
        val pc = ProjectContext(project, pluginConfig.get())
        QueryTasksImpl(pc).listCPIs()
    }
}