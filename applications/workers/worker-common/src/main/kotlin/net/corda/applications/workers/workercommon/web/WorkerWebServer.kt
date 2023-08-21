package net.corda.applications.workers.workercommon.web

import net.corda.applications.workers.workercommon.web.WebContext

interface WorkerWebServer {

    val port: Int?

    fun listen(port: Int)

    fun stop()

    fun get(endpoint: String, handle: (WebContext) -> WebContext)
    fun post(endpoint: String, handle: (WebContext) -> WebContext)
}