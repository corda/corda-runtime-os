package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates a requested resource is unavailable.
 *
 * @param message the exception message
 */
class ServiceUnavailableException(message: String) : HttpApiException(ResponseCode.SERVICE_UNAVAILABLE, message) {
    /**
     * @param resource The resource which is unavailable.
     * @param id The ID of the resource.
     */
    constructor(resource: Any, id: String) : this("$resource '$id' is unavailable.")
}