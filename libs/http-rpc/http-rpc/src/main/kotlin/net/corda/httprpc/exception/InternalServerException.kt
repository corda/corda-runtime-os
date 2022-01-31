package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * The server encountered an unexpected condition which prevented it from fulfilling the request.
 *
 * @param message the response message
 * @param details additional problem details
 */
open class InternalServerException(
    message: String = "Internal server error.",
    details: Map<String, String> = emptyMap()
) : HttpApiException(
    ResponseCode.INTERNAL_SERVER_ERROR,
    message,
    details
)