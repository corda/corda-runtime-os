package net.corda.applications.workers.workercommon

/**
 * Exposes an HTTP endpoint to report health, status and metrics for the worker.
 *
 * A worker indicates its healthiness/readiness by returning a 200 code for HTTP requests to
 * `HTTP_HEALTH_ROUTE`/`HTTP_READINESS_ROUTE`.
 * Worker metrics are reported on `HTTP_METRICS_ROUTE`.
 *
 * A worker is considered healthy if no component has a `LifecycleStatus` of `LifecycleStatus.ERROR`. A worker is
 * considered ready if no component has a `LifecycleStatus` of either `LifecycleStatus.DOWN` or `LifecycleStatus.ERROR`.
 */
interface WorkerMonitor {
    /** registers health and readiness endpoints against the workers webserver. */
    fun registerEndpoints(workerType: String)

}