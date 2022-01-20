package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Base class for HTTP exceptions.
 *
 * Inherit from this class and override the status code.
 *
 * @param responseCode HTTP error response code
 * @param message the response message
 * @param details additional problem details
 * @param cause the original cause of the exception for logging purposes.
 */
abstract class HttpApiException(
    val responseCode: ResponseCode,
    override val message: String?,
    val details: Map<String, String>?,
    cause: Throwable?
) : CordaRuntimeException(message, cause) {

    constructor() : this(ResponseCode.UNEXPECTED_ERROR, null, emptyMap(), null)
    constructor(e: Throwable) : this(ResponseCode.UNEXPECTED_ERROR, null, emptyMap(), e)
    constructor(message: String) : this(ResponseCode.UNEXPECTED_ERROR, message, emptyMap(), null)
    constructor(message: String, e: Throwable) : this(ResponseCode.UNEXPECTED_ERROR, message, emptyMap(), e)
    constructor(message: String, details: Map<String, String>) : this(ResponseCode.UNEXPECTED_ERROR, message, details, null)
    constructor(message: String, details: Map<String, String>, e: Throwable) : this(ResponseCode.UNEXPECTED_ERROR, message, details, e)
    constructor(responseCode: ResponseCode, message: String) : this(responseCode, message, emptyMap(), null)
    constructor(responseCode: ResponseCode, message: String, e: Throwable) : this(responseCode, message, emptyMap(), e)
    constructor(responseCode: ResponseCode, message: String, details: Map<String, String>) : this(responseCode, message, details, null)
}