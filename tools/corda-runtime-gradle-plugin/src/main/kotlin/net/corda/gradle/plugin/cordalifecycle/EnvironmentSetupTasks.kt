package net.corda.gradle.plugin.cordalifecycle

import kong.unirest.Unirest
import net.corda.gradle.plugin.configuration.PluginConfiguration
import net.corda.gradle.plugin.configuration.ProjectContext
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

// Task Group Names
const val UTIL_TASK_GROUP = "corda-runtime-plugin-supporting"

// Configuration Names
const val POSTGRES_JDBC_CFG = "myPostgresJDBC"

// Setup task names
const val PROJINIT_TASK_NAME = "projInit"
const val GET_POSTGRES_JDBC_TASK_NAME = "getPostgresJDBC"
const val GET_COMBINED_WORKER_JAR_TASK_NAME = "getCombinedWorkerJar"
const val GET_NOTARY_SERVER_CPB_TASK_NAME = "getNotaryServerCPB"
const val UPDATE_PROCESSOR_TIMEOUT = "updateProcessorTimeout"

/**
 * Creates the supporting gradle tasks for downloading the combined worker, postgres, also creates
 * the workspace directory
 */
fun createPluginEnvSetupTasks(project: Project, pluginConfig: PluginConfiguration) {
    // Note, project.afterEvaluate {} runs the provided lambda after the rest of the build
    // script has been read. This is important because if the below code is evaluated at
    // this point in the initialisation then the extension block has not yet been read and
    // will contain the default values, ie overriding in the extension block won't have any effect.
    project.afterEvaluate {
        val pc = ProjectContext(project, pluginConfig)

        val postgresJDBCConfig = project.configurations.create(POSTGRES_JDBC_CFG) { conf ->
            conf.isCanBeConsumed = false
            conf.isCanBeResolved = true
        }

        val postgresJDBCDep =
            project.dependencies.create("org.postgresql:postgresql:${pc.postgresJdbcVersion}")
        postgresJDBCConfig.dependencies.add(postgresJDBCDep)

        project.tasks.create(GET_POSTGRES_JDBC_TASK_NAME, Copy::class.java) {
            it.group = UTIL_TASK_GROUP
            it.from(postgresJDBCConfig)
            it.into(pc.jdbcDir)
        }

        project.tasks.create(PROJINIT_TASK_NAME, ProjInit::class.java) {
            it.group = UTIL_TASK_GROUP
            it.pluginConfig.set(pluginConfig)
        }

        project.tasks.create(GET_COMBINED_WORKER_JAR_TASK_NAME, DownloadCombinedWorkerJar::class.java) {
            it.group = UTIL_TASK_GROUP
            it.pluginConfig.set(pluginConfig)
        }

        project.tasks.create(GET_NOTARY_SERVER_CPB_TASK_NAME, DownloadNotaryCpb::class.java) {
            it.group = UTIL_TASK_GROUP
            it.pluginConfig.set(pluginConfig)
        }

        project.tasks.create(UPDATE_PROCESSOR_TIMEOUT, UpdateClusterConfig::class.java) {
            it.group = UTIL_TASK_GROUP
            it.pluginConfig.set(pluginConfig)
        }
    }
}

open class ProjInit @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun projInit() {
        val pc = ProjectContext(project, pluginConfig.get())
        File("${project.rootDir}/${pc.workspaceDir}").mkdirs()
    }
}

open class DownloadCombinedWorkerJar @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun downloadCombinedWorker() {
        val pc = ProjectContext(project, pluginConfig.get())
        EnvironmentSetupHelper().downloadCombinedWorker(
            pc.combinedWorkerFileName,
            pc.combinedWorkerVersion,
            pc.cordaReleaseBranchName,
            pc.combinedWorkerFilePath,
            pc.artifactoryUsername,
            pc.artifactoryPassword
        )
    }
}

open class DownloadNotaryCpb @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun downloadNotaryCpb() {
        val pc = ProjectContext(project, pluginConfig.get())
        EnvironmentSetupHelper().downloadNotaryCpb(
            pc.notaryVersion,
            pc.cordaReleaseBranchName,
            pc.notaryCpbFilePath,
            pc.artifactoryUsername,
            pc.artifactoryPassword
        )
    }
}

open class UpdateClusterConfig @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Input
    val pluginConfig: Property<PluginConfiguration> = objects.property(PluginConfiguration::class.java)

    @TaskAction
    fun updateClusterMessagingConfig() {
        val pc = ProjectContext(project, pluginConfig.get())
        if (pc.cordaProcessorTimeout == (-1).toLong()) {
            return
        }
        val helper = EnvironmentSetupHelper()
        Unirest.config().verifySsl(false)
        val configSection = "corda.messaging"
        val configVersion = helper.getConfigVersion(pc.cordaClusterURL, pc.cordaRpcUser, pc.cordaRpcPassword, configSection)
        val configBody = """
                "subscription": {
                    "processorTimeout": ${pc.cordaProcessorTimeout}
                }
            """.trimIndent()
        helper.sendUpdate(
            pc.cordaClusterURL,
            pc.cordaRpcUser,
            pc.cordaRpcPassword,
            configSection,
            configBody,
            configVersion
        )
        logger.quiet("Updated $configSection processorTimeout to ${pc.cordaProcessorTimeout}")
    }
}
