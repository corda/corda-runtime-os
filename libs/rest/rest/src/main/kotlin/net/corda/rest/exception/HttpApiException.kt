package net.corda.rest.exception

import net.corda.rest.ResponseCode
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Base class for HTTP exceptions.
 *
 * Inherit from this class and override the status code to create a HTTP response with a certain status code ([ResponseCode.statusCode]).
 *
 * @param responseCode HTTP error response code
 * @param title the response title
 * @param details additional problem details
 * @param exceptionDetails contains cause and reason
 */
abstract class HttpApiException(
    val responseCode: ResponseCode,
    val title: String,
    val details: Map<String, String> = emptyMap(),
    val exceptionDetails: ExceptionDetails? = null
) : CordaRuntimeException(title)

data class ExceptionDetails(
    val cause: String,
    val reason: String
)
