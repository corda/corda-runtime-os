package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.isPortInUse
import java.io.File

/**
 * Manages Starting and stopping the local Corda Combined Worker.
 */
class CordaLifecycleTaskImpl(var pc: ProjectContext) {

    private val cordaLifecycleHelper = CordaLifecycleHelper()

    fun startCorda() {
        var exceptionMessage: String? = null
        val cordaPIDFile = File(pc.cordaPidCache)
        if (cordaPIDFile.exists()) {
            exceptionMessage = "Cannot start the Combined worker. Cached process ID file $cordaPIDFile existing. " +
                    "Was the combined worker already started?"
        } else if (isPortInUse(pc.cordaClusterHost, pc.cordaClusterPort)) {
            exceptionMessage = "Port ${pc.cordaClusterPort} is unavailable and is required to start the Combined Worker. " +
                    "Free the port before starting again."
        }
        exceptionMessage?.let {
            throw CordaRuntimeGradlePluginException(it)
        }

        pc.logger.quiet("Starting Docker postgres container.")
        val dockerProcess = cordaLifecycleHelper.startPostgresContainer(pc.cordaDbContainerName)
        val dockerCmdError = dockerProcess.errorStream.bufferedReader().use { it.readText() }
        pc.logger.quiet(dockerCmdError)

        // Fail if Docker is not running before going on to start Corda
        val dockerNotRunningError = "the docker daemon"

        if (dockerCmdError.contains(dockerNotRunningError)) {
            throw CordaRuntimeGradlePluginException(dockerCmdError)
        }

        // Wait for the container to be running before starting Corda
        pc.logger.quiet("Waiting for the Db Docker container to be running")
        cordaLifecycleHelper.waitForContainerStatus(pc.cordaDbContainerName)
        val cordaProcess = cordaLifecycleHelper.startCombinedWorkerProcess(
            pc.cordaPidCache,
            pc.combinedWorkerFilePath,
            pc.javaBinDir,
            pc.jdbcDir,
            pc.project.rootDir.absolutePath
        )
        pc.logger.quiet("Corda Process-id=" + cordaProcess.pid())
    }

    fun stopCorda() {
        cordaLifecycleHelper.stopDockerContainer(pc.cordaDbContainerName)
        val cordaPIDFile = File(pc.cordaPidCache)
        if (!cordaPIDFile.exists()) {
            throw CordaRuntimeGradlePluginException(
                "Cannot stop the Combined worker. Cached process ID file ${pc.cordaPidCache} missing. " +
                        "Was the combined worker not started?"
            )
        }
        cordaLifecycleHelper.stopCombinedWorkerProcess(pc.cordaPidCache)
    }

    fun stopCordaAndCleanWorkspace() {
        try {
            stopCorda()
        } catch (e: CordaRuntimeGradlePluginException) {
            pc.logger.info("Failed to run 'stopCorda'. Was the combined worker already stopped?")
        }

        val workspacePath = File("${pc.project.rootDir}/${pc.workspaceDir}")
        val dirDeleted = workspacePath.deleteRecursively()
        if (!dirDeleted) {
            throw CordaRuntimeGradlePluginException(
                "Failed to delete ${workspacePath.absolutePath}, please remove before starting Corda again."
            )
        }
        pc.logger.quiet("Successfully deleted '${workspacePath.name}' folder")
    }
}