package net.corda.httprpc.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception from HTTP APIs that allows optionally overriding the HTTP status code.
 *
 * For subclasses that aren't expected to know about status codes, secondary constructor can be used leaving [statusCode] null.
 *
 * Server implementation will be responsible for mapping subclasses to the correct exception response type, utilizing [statusCode] or not.
 */
open class HttpApiException(override val message: String, val statusCode: Int?) : CordaRuntimeException(message) {
    constructor(message: String) : this(message, null)
}