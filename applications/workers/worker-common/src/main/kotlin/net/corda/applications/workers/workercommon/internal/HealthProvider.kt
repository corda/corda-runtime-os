package net.corda.applications.workers.workercommon.internal

import java.io.File
import java.io.IOException

/**
 * Allows the health of the worker to be monitored by external processes.
 *
 * The worker is considered healthy if and only if a file exists at `/tmp/worker_is_healthy`. Otherwise, the worker is
 * considered unhealthy. Docker Healthcheck and Kubernetes liveness probes can therefore monitor the health of the
 * worker using the command `cat /tmp/worker_is_healthy`, which will return 0 if the file exists, and 1 otherwise.
 *
 * Since this file does not exist initially, the worker starts in an unhealthy state. Health checks should therefore be
 * delayed until an initial call to [setHealthy].
 */
class HealthProvider {
    companion object {
        private val healthCheckFile = File(HEALTH_CHECK_FILE_NAME).apply {
            deleteOnExit()
        }
    }

    /** Marks the worker as healthy, by creating the health-check file. */
    fun setHealthy() {
        // We are not running on a Unix-like operating system. This means that we are running outside of a container,
        // and thus a health-check isn't required.
        if (!File("/tmp/").exists()) return
        healthCheckFile.createNewFile()
    }

    /** Marks the worker as unhealthy, by deleting the health-check file. */
    fun setUnhealthy() {
        healthCheckFile.delete()
    }
}