package net.corda.web.api

import java.net.MalformedURLException
import java.net.URL
import net.corda.v5.base.exceptions.CordaRuntimeException


data class Endpoint(val methodType: HTTPMethod, val endpoint: String, val webHandler: WebHandler) {
    fun validate() {
        val error = StringBuilder()
        if (endpoint.isBlank()) error.appendLine("Endpoint must not be empty")
        if (!endpoint.startsWith("/")) error.appendLine("Endpoint $endpoint must start with '/'")
        if (!isValidEndpoint(endpoint)) error.appendLine("Endpoint $endpoint is not validly formed")

        if (error.isNotEmpty()) {
            throw CordaRuntimeException(error.toString())
        }
    }

    private fun isValidEndpoint(endpoint: String): Boolean {
        return try {
            URL("http://test$endpoint")
            true
        } catch (e: MalformedURLException) {
            false
        }
    }
}