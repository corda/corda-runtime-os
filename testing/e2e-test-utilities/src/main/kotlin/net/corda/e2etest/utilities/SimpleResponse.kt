package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Needs to handle duplicates in keys, hence cannot use `Map` */
typealias Headers = List<Pair<String, String>>

/** Simplified response in case we switch underlying web clients, again */
data class SimpleResponse(val code: Int, val body: String, val url: String, val headers: Headers) {
    fun toJson(): JsonNode = ObjectMapper().readTree(this.body)!!
}
