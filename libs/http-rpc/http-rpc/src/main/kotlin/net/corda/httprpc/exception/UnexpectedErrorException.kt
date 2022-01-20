package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates some unexpected internal server error. Translates to a status code of 500.
 *
 * @param message the response message
 * @param details additional problem details
 */
open class UnexpectedErrorException(message: String, details: Map<String, String>) : HttpApiException(
    ResponseCode.UNEXPECTED_ERROR,
    message,
    details
) {
    constructor() : this("Unknown error occurred.", emptyMap())
    constructor(message: String) : this(message, emptyMap())
}