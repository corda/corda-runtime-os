package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * User authentication has failed.
 */
class NotAuthenticatedException(message: String = "User authentication failed.", details: Map<String, String> = emptyMap()) :
    HttpApiException(
        ResponseCode.NOT_AUTHENTICATED,
        message,
        details
    )