package net.corda.crypto.rest

import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.rest.response.ResponseEntity

/**
 * Key Rotation API consists of endpoints to request a key rotation operation and to check the status of such operation,
 */
@HttpRestResource(
    name = "Key Rotation API",
    description = "Contains operations related to rotation of the wrapping keys.",
    path = "wrappingkey",
    minVersion = RestApiVersion.C5_1
)
interface KeyRotationRestResource : RestResource {
    /**
     * Initialises the API implementation. This method may be called multiple times throughout the life
     * of the API.
     * @param config A config
     */
    fun initialise(config: Map<String, SmartConfig>)

    /**
     * The [getKeyRotationStatus] gets a list of unmanaged wrapping keys [{alias, [requestIds]}] where requestIds is
     *                         list of rotations runs in progress.
     *
     * @return A list of unmanaged wrapping keys [{alias, [requestIds]}] where requestIds is
     *         the list of rotations runs in progress.
     */
    @HttpGET(
        path = "unmanaged/rotation",
        description = "This method gets the status of the current rotation.",
        responseDescription = "",
    )
    fun getKeyRotationStatus(): List<Pair<String, List<String>>>

    /**
     * Initiates the key rotation process. 
     *
     * @param oldKeyAlias Alias of the key to be rotated.
     * @param newKeyAlias Alias of the new key the [oldKeyAlias] key will be rotated with.
     * @param simulate If true, only work out the number of keys to rotate.
     * @param timeToLive The maximum number of milliseconds which should pass, after which the key rotation loop will exit.
     * @param limit The maximum number of key rotations to do, after which the key rotation loop will exit.
     *
     * @return Key rotation response where
     *  - requestId is the unique ID for the key rotation start request.
     *  - oldKeyAlias is the alias of the key to be rotated.
     *  - newKeyAlias is the alias of the new key the oldKeyAlias key will be rotated with.
     *
     * @throws ServiceUnavailableException If the underlying service for sending messages is not available.
     * @throws InvalidInputDataException If the [oldKeyAlias] or [newKeyAlias] are not part of the supplied configuration.
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
        @ClientRequestBodyParameter(
            description = "If true, only work out the number of keys to rotate.",
            required = false
        )
        simulate: Boolean,
        @ClientRequestBodyParameter(
            description = "The maximum number of milliseconds which should pass, after which the key rotation loop will exit.",
            required = false
        )
        timeToLive: Int,
        @ClientRequestBodyParameter(
            description = "The maximum number of key rotations to do, after which the key rotation loop will exit.",
            required = false
        )
        limit: Int,
    ): ResponseEntity<KeyRotationResponse>
}
