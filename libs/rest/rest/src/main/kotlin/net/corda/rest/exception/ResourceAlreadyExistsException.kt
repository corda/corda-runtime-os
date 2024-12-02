package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested resource already exists within the system
 *
 * @param title the exception title
 * @param exceptionDetails contains cause and reason
 */
class ResourceAlreadyExistsException(title: String, exceptionDetails: ExceptionDetails? = null) :
    HttpApiException(ResponseCode.CONFLICT, title, exceptionDetails = exceptionDetails) {
    /**
     * @param resource The resource which already exists
     * @param id The ID of the resource.
     */
    constructor(
        resource: Any,
        id: String,
        exceptionDetails: ExceptionDetails? = null
    ) : this("$resource '$id' already exists.", exceptionDetails)
}
