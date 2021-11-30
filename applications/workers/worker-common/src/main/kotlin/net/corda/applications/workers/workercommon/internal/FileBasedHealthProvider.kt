package net.corda.applications.workers.workercommon.internal

import net.corda.applications.workers.workercommon.HealthProvider
import java.io.File

/**
 * With this health provider, the worker is considered healthy if and only if [healthCheckFile] file exists. Docker
 * Healthcheck and Kubernetes liveness probes can therefore monitor the health of the worker using the command
 * `cat [healthCheckFile]`, which will return 0 if the file exists, and 1 otherwise.
 *
 * Since this file does not exist initially, the worker starts in an unhealthy state. Health checks should therefore be
 * delayed until an initial call to [setHealthy].
 */
internal class FileBasedHealthProvider : HealthProvider {
    private val healthCheckFile = File(HEALTH_CHECK_PATH_NAME).apply {
        deleteOnExit()
    }

    /** Marks the worker as healthy by creating the health-check file. */
    override fun setHealthy() {
        // We are not running on a Unix-like operating system. This means that we are running outside a container, and
        // thus a health-check isn't required.
        if (!File(UNIX_TMP_DIR).exists()) return
        healthCheckFile.createNewFile()
    }

    /** Marks the worker as unhealthy by deleting the health-check file. */
    override fun setUnhealthy() {
        healthCheckFile.delete()
    }
}