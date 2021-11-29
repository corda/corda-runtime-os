package net.corda.applications.workers.workercommon

/**
 * Allows the health of the worker to be monitored by external processes.
 *
 * The worker is considered healthy if and only if a file exists at `/tmp/worker_is_healthy`. Otherwise, the worker is
 * considered unhealthy. Docker Healthcheck and Kubernetes liveness probes can therefore monitor the health of the
 * worker using the command `cat /tmp/worker_is_healthy`, which will return 0 if the file exists, and 1 otherwise.
 *
 * Since this file does not exist initially, the worker starts in an unhealthy state. Health checks should therefore be
 * delayed until an initial call to [setIsHealthy].
 */
interface HealthProvider {
    /** Marks the worker as healthy. */
    fun setIsHealthy()

    /** Marks the worker as unhealthy. */
    fun setIsUnhealthy()
}