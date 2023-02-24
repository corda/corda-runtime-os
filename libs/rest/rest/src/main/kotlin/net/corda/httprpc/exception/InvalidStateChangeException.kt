package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates a requested resource already exists within the system in the current state
 *
 * @param message the exception message
 */
class InvalidStateChangeException(message: String) : HttpApiException(ResponseCode.CONFLICT, message) {
    /**
     * @param resource The resource which the state effects
     * @param id The ID of the resource.
     */
    constructor(resource: Any, state: String, id: String) : this("$resource '$id' is already in $state state.")
}
