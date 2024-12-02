package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * User authentication has failed.
 */
class NotAuthenticatedException(
    title: String = "User authentication failed.",
    details: Map<String, String> = emptyMap(),
    exceptionDetails: ExceptionDetails? = null
) :
    HttpApiException(
        ResponseCode.NOT_AUTHENTICATED,
        title,
        details,
        exceptionDetails
    )
