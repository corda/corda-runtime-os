package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates a requested state change matches the resources current state
 *
 * @param message the exception message
 */
class InvalidStateChangeException(message: String) : HttpApiException(ResponseCode.CONFLICT, message)
