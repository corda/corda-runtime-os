package net.corda.applications.workers.smoketest.virtualnode.helpers

/** Simplified response in case we switch underlying web clients, again */
data class SimpleResponse(val code: Int, val body: String, val url: String)
