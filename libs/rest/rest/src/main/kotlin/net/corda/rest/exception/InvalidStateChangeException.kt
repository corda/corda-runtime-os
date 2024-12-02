package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested state change matches the resources current state
 *
 * @param title the exception title
 * @param exceptionDetails contains cause and reason
 */
class InvalidStateChangeException(title: String, exceptionDetails: ExceptionDetails? = null) :
    HttpApiException(ResponseCode.CONFLICT, title, exceptionDetails = exceptionDetails)
