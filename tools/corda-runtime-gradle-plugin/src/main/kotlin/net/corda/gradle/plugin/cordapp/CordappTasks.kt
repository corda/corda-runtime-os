package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.configuration.PluginConfiguration
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.cordalifecycle.GET_NOTARY_SERVER_CPB_TASK_NAME
import net.corda.gradle.plugin.cordalifecycle.PROJINIT_TASK_NAME
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

// Tasks relating to building a CorDapp
const val CORDAPP_BUILD_GROUP = "corda-runtime-plugin-cordapp"
const val CREATE_GROUP_POLICY_TASK_NAME = "createGroupPolicy"
const val CREATE_KEYSTORE_TASK_NAME = "createKeystore"
const val BUILD_CPIS_TASK_NAME = "buildCpis"
const val DEPLOY_CPIS_TASK_NAME = "deployCpis"
const val DEPLOY_MGMS_TASK_NAME = "deployMGM"
const val EXTRACT_DYNAMIC_NETWORK_POLICY_TASK_NAME = "extractGroupPolicyFromMgm"
const val WORKFLOW_BUILD_COMMAND = ":workflows:build"
const val NOTARY_CPB_COMMAND = ":notary:cpb"

fun createCordappTasks(project: Project, pluginConfiguration: PluginConfiguration) {
    project.afterEvaluate {
        project.tasks.create(CREATE_GROUP_POLICY_TASK_NAME, CreateGroupPolicyTask::class.java) {
            it.group = CORDAPP_BUILD_GROUP
            it.dependsOn(PROJINIT_TASK_NAME)
            it.pluginConfig.set(pluginConfiguration)
        }

        project.tasks.create(CREATE_KEYSTORE_TASK_NAME, CreateKeystoreTask::class.java) {
            it.group = CORDAPP_BUILD_GROUP
            it.dependsOn(CREATE_GROUP_POLICY_TASK_NAME)
            it.pluginConfig.set(pluginConfiguration)
        }

        project.tasks.create(DEPLOY_MGMS_TASK_NAME, DeployMgmCPITask::class.java) {
            it.group = CORDAPP_BUILD_GROUP
            it.dependsOn(CREATE_KEYSTORE_TASK_NAME)
            it.pluginConfig.set(pluginConfiguration)
        }

        project.tasks.create(EXTRACT_DYNAMIC_NETWORK_POLICY_TASK_NAME, ExtractDynamicGroupPolicyTask::class.java) {
            it.group = CORDAPP_BUILD_GROUP
            it.dependsOn(DEPLOY_MGMS_TASK_NAME)
            it.pluginConfig.set(pluginConfiguration)
        }


        project.tasks.create(BUILD_CPIS_TASK_NAME, BuildCPIsTask::class.java) {
            it.group = CORDAPP_BUILD_GROUP
            it.dependsOn(
                CREATE_KEYSTORE_TASK_NAME,
                getWorkflowsModuleTaskName(pluginConfiguration)
            )
            val pc = ProjectContext(project, pluginConfiguration)
            if (pc.isNotaryNonValidating) {
                it.dependsOn(GET_NOTARY_SERVER_CPB_TASK_NAME)
            } else {
                it.dependsOn(NOTARY_CPB_COMMAND.replace("notary", pluginConfiguration.notaryModuleName.get()))
            }

            if (pc.networkConfig.mgmNodeIsPresentInNetworkDefinition) {
                it.dependsOn(EXTRACT_DYNAMIC_NETWORK_POLICY_TASK_NAME)
            }
            it.pluginConfig.set(pluginConfiguration)
        }

        project.tasks.create(DEPLOY_CPIS_TASK_NAME, DeployCPIsTask::class.java) {
            it.group = CORDAPP_BUILD_GROUP
            it.dependsOn(BUILD_CPIS_TASK_NAME)
            it.pluginConfig.set(pluginConfiguration)
        }


    }
}

private fun getWorkflowsModuleTaskName(pluginConfiguration: PluginConfiguration): String {
    var workflowsModuleTaskName = WORKFLOW_BUILD_COMMAND.replace("workflows", pluginConfiguration.workflowsModuleName.get())
    if (pluginConfiguration.skipTestsDuringBuildCpis.get().toBoolean()) {
        // If we want to skip the tests, we can use the assemble task rather than the build task
        workflowsModuleTaskName = workflowsModuleTaskName.replace(":build", ":assemble")
    }
    return workflowsModuleTaskName
}

open class CreateGroupPolicyTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun createGroupPolicy() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordappTasksImpl(pc).createGroupPolicy()
    }
}

open class CreateKeystoreTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun createKeystore() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordappTasksImpl(pc).createKeyStore()
    }
}

open class BuildCPIsTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun buildCPIs() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordappTasksImpl(pc).buildCPIs()
    }
}

open class DeployCPIsTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun deployCPIs() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordappTasksImpl(pc).deployCPIs()
    }
}

open class DeployMgmCPITask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun deployMgmCPI() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordappTasksImpl(pc).deployMgmCpi()
    }
}

open class ExtractDynamicGroupPolicyTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun extractPolicyFromMgm() {
        val pc = ProjectContext(project, pluginConfig.get())
        CordappTasksImpl(pc).extractGroupPolicyFromMgm()
    }
}