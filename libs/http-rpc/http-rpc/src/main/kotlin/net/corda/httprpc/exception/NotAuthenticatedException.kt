package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

class NotAuthenticatedException(message: String, details: Map<String, String>) : HttpApiException(
    ResponseCode.NOT_AUTHENTICATED,
    message,
    details
) {
    constructor(): this("User authentication failed.", emptyMap())
    constructor(message: String): this(message, emptyMap())
}