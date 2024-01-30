package net.corda.gradle.plugin.configuration

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Class which holds all the context properties for the gradle build. This is split between:
 * - Properties which are obtained from the csde block in the csde build.gralde file
 * - The network config
 * - Properties which are non-configurable by the user
 * A version of this class will typically be passed to each of the helper classes.
 */
class ProjectContext(val project: Project, pluginConfig: PluginConfiguration) {

    // Capture values of user configurable context properties items from pluginConfig
    val cordaClusterURL: String = pluginConfig.cordaClusterURL.get()
    val cordaRpcUser: String = pluginConfig.cordaRpcUser.get()
    val cordaRpcPassword: String = pluginConfig.cordaRpcPasswd.get()
    val workspaceDir: String = pluginConfig.cordaRuntimePluginWorkspaceDir.get()
    val combinedWorkerVersion: String = pluginConfig.combinedWorkerVersion.get()
    val postgresJdbcVersion: String = pluginConfig.postgresJdbcVersion.get()
    val cordaDbContainerName: String =pluginConfig.cordaDbContainerName.get()
    val cordaBinDir: String = pluginConfig.cordaBinDir.get()
    val cordaCliBinDir: String = pluginConfig.cordaCliBinDir.get()
    val artifactoryUsername: String = pluginConfig.artifactoryUsername.get()
    val artifactoryPassword: String = pluginConfig.artifactoryPassword.get()

    // Set Non user configurable context properties
    val javaBinDir: String = "${System.getProperty("java.home")}/bin"
    val cordaPidCache: String = "$workspaceDir/CordaPIDCache.dat"
    val jdbcDir: String = "$cordaBinDir/jdbcDrivers"

    val cordaClusterHost: String = cordaClusterURL.split("://").last().split(":").first()
    val cordaClusterPort: Int = cordaClusterURL.split("://").last().split(":").last().toInt()

    val combinedWorkerFileName: String = "corda-combined-worker-$combinedWorkerVersion.jar"
    val combinedWorkerFilePath: String = "$cordaBinDir/combinedWorker/$combinedWorkerFileName"
    val cordaReleaseBranchName: String = "release-$combinedWorkerVersion"

    val logger: Logger = project.logger
}