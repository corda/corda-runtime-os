package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates the request contained invalid input data and the request could not be serviced.
 *
 * @param message the response message
 * @param details additional problem details
 */
open class BadRequestException(message: String, details: Map<String, String>) : HttpApiException(
    ResponseCode.BAD_REQUEST,
    message,
    details
) {
    constructor(message: String) : this(message, emptyMap())
}