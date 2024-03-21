package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested resource is in an incompatible state with the request.
 *
 * @param title the exception title
 * @param exceptionDetails contains cause and reason
 */
class OperationNotAllowedException(title: String, exceptionDetails: ExceptionDetails? = null) :
    HttpApiException(ResponseCode.METHOD_NOT_ALLOWED, title, exceptionDetails = exceptionDetails)
