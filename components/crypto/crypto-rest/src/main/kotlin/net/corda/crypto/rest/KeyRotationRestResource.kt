package net.corda.crypto.rest

import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.crypto.rest.response.KeyRotationStatusResponse
import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.exception.ForbiddenException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.rest.response.ResponseEntity

/**
 * Key Rotation API consists of endpoints to request a key rotation operation and to check the status of such operation.
 */
@HttpRestResource(
    name = "Key Rotation API",
    description = "Contains operations related to rotation of the wrapping keys.",
    path = "wrappingkey",
    minVersion = RestApiVersion.C5_2
)
interface KeyRotationRestResource : RestResource {
    /**
     * The [getKeyRotationStatus] gets the latest key rotation status for [keyAlias] if one exists.
     *
     * @return A list of vNodes with the total number of keys needs re-rewrapping and the number of already re-wrapped
     *          keys.
     *
     * @throws ResourceNotFoundException If no key rotation was in progress for [keyAlias].
     */
    @HttpGET(
        path = "unmanaged/rotation/{keyAlias}",
        description = "This method gets the status of the latest key rotation.",
        responseDescription = "Number of wrapping keys needs rotating grouped by vNode.",
    )
    fun getKeyRotationStatus(
        @RestPathParameter(description = "The keyAlias we are rotating away from.")
        keyAlias: String
    ): KeyRotationStatusResponse

    /**
     * Initiates the key rotation process. 
     *
     * @param oldKeyAlias Alias of the key to be rotated.
     * @param newKeyAlias Alias of the new key the [oldKeyAlias] key will be rotated with.
     *
     * @return Key rotation response where
     *  - requestId is the unique ID for the key rotation start request.
     *  - oldKeyAlias is the alias of the key to be rotated.
     *  - newKeyAlias is the alias of the new key the oldKeyAlias key will be rotated with.
     *
     * @throws ServiceUnavailableException If the underlying service for sending messages is not available.
     * @throws ForbiddenException If the same key rotation is already in progress.
     */

    @HttpPOST(
        path = "unmanaged/rotation/{oldKeyAlias}",
        description = "This method enables to rotate a current wrapping key with a new wrapping key.",
        responseDescription = "Key rotation response",
    )
    fun startKeyRotation(
        @RestPathParameter(
            description = "The alias of the current wrapping key to be rotated."
        )
        oldKeyAlias: String,
        @ClientRequestBodyParameter(
            description = "The alias of the new wrapping key that old one will be rotated with.",
            required = true
        )
        newKeyAlias: String,
    ): ResponseEntity<KeyRotationResponse>
}
