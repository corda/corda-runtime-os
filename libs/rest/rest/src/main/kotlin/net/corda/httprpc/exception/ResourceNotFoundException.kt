package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates a requested resource does not exist.
 *
 * @param message the exception message
 */
class ResourceNotFoundException(message: String) : HttpApiException(ResponseCode.RESOURCE_NOT_FOUND, message) {
    /**
     * @param resource The resource which could not be found.
     * @param id The ID of the resource.
     */
    constructor(resource: Any, id: String) : this("$resource '$id' not found.")
}