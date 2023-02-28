package net.corda.rest.exception

import net.corda.rest.ResponseCode

/**
 * Indicates a requested resource already exists within the system
 *
 * @param message the exception message
 */
class ResourceAlreadyExistsException(message: String) : HttpApiException(ResponseCode.CONFLICT, message) {
    /**
     * @param resource The resource which already exists
     * @param id The ID of the resource.
     */
    constructor(resource: Any, id: String) : this("$resource '$id' already exists.")
}
