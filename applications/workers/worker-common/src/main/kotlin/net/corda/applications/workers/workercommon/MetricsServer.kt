package net.corda.applications.workers.workercommon

interface MetricsServer {
    /** Serves worker health and readiness on [port]. */
    fun listen(port: Int)

    /** Stops serving worker health and readiness. */
    fun stop()

    /** The port the health monitor listens on, once it has successfully managed to listen on a socket */
    val port: Int?
}