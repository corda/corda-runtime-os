package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested resource does not exist.
 *
 * @param title the exception title
 * @param exceptionDetails contains cause and reason
 */
class ResourceNotFoundException(title: String, exceptionDetails: ExceptionDetails? = null) :
    HttpApiException(ResponseCode.RESOURCE_NOT_FOUND, title, exceptionDetails = exceptionDetails) {
    /**
     * @param resource The resource which could not be found.
     * @param id The ID of the resource.
     */
    constructor(
        resource: Any,
        id: String,
        exceptionDetails: ExceptionDetails? = null
    ) : this("$resource '$id' not found.", exceptionDetails)
}
