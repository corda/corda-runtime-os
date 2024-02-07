package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.configuration.PluginConfiguration
import net.corda.gradle.plugin.configuration.ProjectContext
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

const val CLUSTER_TASKS_GROUP = "corda-runtime-plugin-local-environment"
const val START_CORDA_TASK_NAME = "startCorda"
const val STOP_CORDA_TASK_NAME = "stopCorda"
const val STOP_CORDA_AND_CLEAN_TASK_NAME = "stopCordaAndCleanWorkspace"

// Corda lifecycle tasks in here, such as start/stop the CombinedWorker
fun createCordaLifeCycleTasks(project: Project, pluginConfig: PluginConfiguration) {

    project.afterEvaluate {

        project.tasks.create(START_CORDA_TASK_NAME, StartCorda::class.java) {
            it.group = CLUSTER_TASKS_GROUP
            it.dependsOn(PROJINIT_TASK_NAME)
            it.pluginConfig.set(pluginConfig)
        }

        project.tasks.create(STOP_CORDA_TASK_NAME, StopCorda::class.java) {
            it.group = CLUSTER_TASKS_GROUP
            it.pluginConfig.set(pluginConfig)
        }

        project.tasks.create(STOP_CORDA_AND_CLEAN_TASK_NAME, StopCordaAndCleanWorkspace::class.java) {
            it.group = CLUSTER_TASKS_GROUP
            it.pluginConfig.set(pluginConfig)
        }
    }
}

open class StartCorda @Inject constructor(objects: ObjectFactory): DefaultTask() {

    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun startCorda() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordaLifecycleTaskImpl(pc).startCorda()
    }
}

open class StopCorda @Inject constructor(objects: ObjectFactory): DefaultTask() {

    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun stopCorda() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordaLifecycleTaskImpl(pc).stopCorda()
    }
}

open class StopCordaAndCleanWorkspace @Inject constructor(objects: ObjectFactory): DefaultTask() {

    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun stopCordaAndCleanWorkspace() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordaLifecycleTaskImpl(pc).stopCordaAndCleanWorkspace()
    }
}