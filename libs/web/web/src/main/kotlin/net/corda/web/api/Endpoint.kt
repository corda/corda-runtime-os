package net.corda.web.api

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.commons.validator.routines.UrlValidator


/**
 * Endpoint class that stores a http path with its associated WebHandler. Also contains validation logic
 *
 * @property methodType Http method type
 * @property path url path
 * @property webHandler processing logic used to process request to this path
 * @property isApi flag indicating if this path is an API endpint (and hence should be versioned)
 */
data class Endpoint(val methodType: HTTPMethod, val path: String, val webHandler: WebHandler, val isApi: Boolean = false) {
    fun validate() {
        val error = StringBuilder()
        if (path.isBlank()) error.appendLine("Endpoint must not be empty")
        if (!path.startsWith("/")) error.appendLine("Endpoint $path must start with '/'")
        if (!isValidEndpoint(path)) error.appendLine("Endpoint $path is not validly formed")

        if (error.isNotEmpty()) {
            throw CordaRuntimeException(error.toString())
        }
    }

    private fun isValidEndpoint(endpoint: String): Boolean =
        UrlValidator(UrlValidator.ALLOW_LOCAL_URLS).isValid("http://localhost$endpoint")
}