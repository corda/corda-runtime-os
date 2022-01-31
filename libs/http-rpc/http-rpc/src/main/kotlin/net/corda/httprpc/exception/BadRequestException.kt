package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates the request was syntactically bad or contained invalid input data and the request could not be serviced.
 *
 * @param message the response message
 * @param details additional problem details
 */
class BadRequestException(message: String, details: Map<String, String> = emptyMap()) : HttpApiException(
    ResponseCode.BAD_REQUEST,
    message,
    details
)