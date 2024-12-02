package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * The server encountered an internal error which prevented it from fulfilling the request.
 *
 * @param title the response title
 * @param details additional problem details
 * @param exceptionDetails contains cause and reason
 */
class InternalServerException(
    title: String = "Internal server error.",
    details: Map<String, String> = emptyMap(),
    exceptionDetails: ExceptionDetails? = null
) : HttpApiException(
    ResponseCode.INTERNAL_SERVER_ERROR,
    title,
    details,
    exceptionDetails
)
