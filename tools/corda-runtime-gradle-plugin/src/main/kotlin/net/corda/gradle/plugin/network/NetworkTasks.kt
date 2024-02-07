package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.configuration.PluginConfiguration
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.cordalifecycle.UPDATE_PROCESSOR_TIMEOUT
import net.corda.gradle.plugin.cordapp.DEPLOY_CPIS_TASK_NAME
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

// Tasks relating to creating virtual nodes and setting up a network

const val NETWORK_GROUP = "corda-runtime-plugin-network"
const val VNODE_SETUP_TASK_NAME = "vNodesSetup"

/**
 * Creates the gradle helper tasks in the network group
 */
fun createNetworkTasks(project: Project, pluginConfiguration: PluginConfiguration) {
    project.afterEvaluate {
        project.tasks.create(VNODE_SETUP_TASK_NAME, VNodeSetupTask::class.java) {
            it.group = NETWORK_GROUP
            it.dependsOn(DEPLOY_CPIS_TASK_NAME)
            it.pluginConfig.set(pluginConfiguration)
            it.finalizedBy(UPDATE_PROCESSOR_TIMEOUT)
        }
    }

}

open class VNodeSetupTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun deployCPIs() {
        val pc = ProjectContext(project, pluginConfig.get())
        NetworkTasksImpl(pc).createVNodes()
        NetworkTasksImpl(pc).registerVNodes()
    }
}