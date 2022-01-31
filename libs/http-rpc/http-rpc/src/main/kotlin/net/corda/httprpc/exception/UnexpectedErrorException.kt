package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * The server encountered an unexpected error.
 *
 * @param message the response message
 * @param details additional problem details
 */
class UnexpectedErrorException(message: String = "Unexpected internal error occurred.", details: Map<String, String> = emptyMap()) :
    HttpApiException(
        ResponseCode.UNEXPECTED_ERROR,
        message,
        details
    )