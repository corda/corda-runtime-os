package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Simplified response in case we switch underlying web clients, again */
data class SimpleResponse(val code: Int, val body: String, val url: String) {
    fun toJson(): JsonNode = ObjectMapper().readTree(this.body)!!
}
