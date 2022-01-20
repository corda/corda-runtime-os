package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

class ForbiddenException(message: String, details: Map<String, String>) : HttpApiException(
    ResponseCode.FORBIDDEN,
    message,
    details
) {
    constructor(): this("User not authorized.", emptyMap())
    constructor(message: String): this(message, emptyMap())
}