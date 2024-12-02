package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates the request was syntactically bad or contained invalid input data and the request could not be serviced.
 *
 * @param title the response title
 * @param details additional problem details
 * @param exceptionDetails contains cause and reason
 */
class BadRequestException(
    title: String,
    details: Map<String, String> = emptyMap(),
    exceptionDetails: ExceptionDetails? = null
) : HttpApiException(
    ResponseCode.BAD_REQUEST,
    title,
    details,
    exceptionDetails
)
