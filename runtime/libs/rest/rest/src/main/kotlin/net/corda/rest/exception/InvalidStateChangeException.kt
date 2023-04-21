package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested state change matches the resources current state
 *
 * @param message the exception message
 */
class InvalidStateChangeException(message: String) : HttpApiException(ResponseCode.CONFLICT, message)