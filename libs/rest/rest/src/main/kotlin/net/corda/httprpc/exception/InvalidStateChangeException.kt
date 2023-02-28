package net.corda.httprpc.exception

import net.corda.rest.ResponseCode
import net.corda.rest.exception.HttpApiException

/**
 * Indicates a requested state change matches the resources current state
 *
 * @param message the exception message
 */
class InvalidStateChangeException(message: String) : HttpApiException(ResponseCode.CONFLICT, message)
