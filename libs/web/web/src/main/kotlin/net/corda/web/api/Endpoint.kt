package net.corda.web.api

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.commons.validator.routines.UrlValidator


/**
 * Endpoint class that stores a http endpoint with its associated WebHandler. Also contains validation logic
 *
 * @property methodType Http method type
 * @property endpoint endpoint url
 * @property webHandler processing logic used to process request to this endpoint
 */
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

    private fun isValidEndpoint(endpoint: String): Boolean =
        UrlValidator(UrlValidator.ALLOW_LOCAL_URLS).isValid("http://localhost$endpoint")
}