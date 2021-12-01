package net.corda.applications.workers.healthprovider

/** Allows the health of a worker to be monitored externally. */
interface HealthProvider {
    /** Marks the worker as healthy. */
    fun setHealthy()

    /** Marks the worker as unhealthy. */
    fun setNotHealthy()

    /** Marks the worker as ready. */
    fun setReady()

    /** Marks the worker as not ready. */
    fun setNotReady()
}