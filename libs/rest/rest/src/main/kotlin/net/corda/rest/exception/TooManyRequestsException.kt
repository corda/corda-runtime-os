package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * The server indicates the user has sent too many requests in a given amount of time ("rate limiting").
 *
 * @param message the response message
 * @param details additional problem details
 */
class TooManyRequestsException(message: String = "Too many requests.", details: Map<String, String> = emptyMap()) :
    HttpApiException(
        ResponseCode.TOO_MANY_REQUESTS,
        message,
        details
    )