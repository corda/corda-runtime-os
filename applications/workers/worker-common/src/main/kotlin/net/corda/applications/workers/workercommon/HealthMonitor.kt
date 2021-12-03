package net.corda.applications.workers.workercommon

/**
 * Monitors the health of a worker.
 *
 * A worker indicates its healthiness/readiness by returning a 200 code for HTTP requests to
 * `HTTP_HEALTH_ROUTE`/`HTTP_READINESS_ROUTE`.
 *
 * A worker is considered healthy if no component has a `LifecycleStatus` of `LifecycleStatus.ERROR`. A worker is
 * considered ready if no component has a `LifecycleStatus` of either `LifecycleStatus.DOWN` or `LifecycleStatus.ERROR`.
 */
interface HealthMonitor {
    /** Serves worker health and readiness on [port]. */
    fun listen(port: Int)

    /** Stops serving worker health and readiness. */
    fun stop()
}