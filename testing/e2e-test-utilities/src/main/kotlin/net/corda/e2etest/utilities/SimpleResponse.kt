package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.JsonNode

/** Simplified response in case we switch underlying web clients, again */
data class SimpleResponse(val code: Int, val body: String, val url: String) {
    fun toJson(): JsonNode = objectMapper.readTree(this.body)!!
}
