package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Authorization has failed for the user and prevented the user from carrying out the operation.
 *
 * If the authorization logic wants to hide the fact authorization failed, a [ResourceNotFoundException] can be thrown instead.
 */
class ForbiddenException(message: String = "User not authorized.", details: Map<String, String> = emptyMap()) : HttpApiException(
    ResponseCode.FORBIDDEN,
    message,
    details
)