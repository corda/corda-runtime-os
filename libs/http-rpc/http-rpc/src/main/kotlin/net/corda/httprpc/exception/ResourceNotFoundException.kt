package net.corda.httprpc.exception

import net.corda.httprpc.ResponseCode

/**
 * Indicates a requested resource does not exist.
 *
 * @param resource the resource
 * @param id the identifier for the resource
 */
open class ResourceNotFoundException(resource: String, id: String) : HttpApiException(
    ResponseCode.RESOURCE_NOT_FOUND,
    "$resource $id not found."
) {
    constructor(resource: Any, id: String) : this(resource::class.java.name, id)
}