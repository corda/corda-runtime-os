package net.corda.applications.workers.smoketest.virtualnode.helpers

import com.fasterxml.jackson.databind.ObjectMapper

private val objectMapper = ObjectMapper()

/** Simplified response in case we switch underlying web clients, again */
data class SimpleResponse(val code: Int, val body: String, val url: String) {
    fun toJson() = objectMapper.readTree(this.body)!!
}
