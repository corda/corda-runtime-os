package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested resource is unavailable.
 *
 * @param title the exception title
 * @param exceptionDetails contains cause and reason
 */
class ServiceUnavailableException(title: String, exceptionDetails: ExceptionDetails? = null) :
    HttpApiException(ResponseCode.SERVICE_UNAVAILABLE, title, exceptionDetails = exceptionDetails) {
    /**
     * @param resource The resource which is unavailable.
     * @param id The ID of the resource.
     */
    constructor(
        resource: Any,
        id: String,
        exceptionDetails: ExceptionDetails? = null
    ) : this("$resource '$id' is unavailable.", exceptionDetails)
}
