package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * The server encountered an internal error which prevented it from fulfilling the request.
 *
 * For errors that are unexpected, use [UnexpectedErrorException].
 *
 * @param message the response message
 * @param details additional problem details
 */
class InternalServerException(
    message: String = "Internal server error.",
    details: Map<String, String> = emptyMap()
) : HttpApiException(
    ResponseCode.INTERNAL_SERVER_ERROR,
    message,
    details
)