package net.corda.applications.workers.workercommon

/** Allows the health of a worker to be monitored externally. */
interface HealthProvider {
    /** Marks the worker as healthy. */
    fun setHealthy()

    /** Marks the worker as unhealthy. */
    fun setUnhealthy()
}