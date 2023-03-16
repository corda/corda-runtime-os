package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested resource is in an incompatible state with the request.
 *
 * @param message the exception message
 */
class OperationNotAllowedException(message: String) : HttpApiException(ResponseCode.METHOD_NOT_ALLOWED, message)