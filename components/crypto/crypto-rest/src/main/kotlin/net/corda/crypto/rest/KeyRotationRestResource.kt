package net.corda.crypto.rest

import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.response.ResponseEntity

/**
 * Key Rotation API consists of endpoints to request a key rotation operation and to check the status of such operation,
 */
@HttpRestResource(
    name = "Key Rotation API",
    description = "Contains operations related to rotation of the wrapping keys.",
    path = "wrappingkeys",
    minVersion = RestApiVersion.C5_1
)
interface KeyRotationRestResource : RestResource {

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
     *  - processedCount is the number of keys that finished rotating.
     *  - duration is the time it took to rotate the keys in processedCount.
     *  - expectedTotal is the number of keys yet to be rotated.
     */

    @HttpPOST(
        path = "unmanaged/rotation/{oldKeyAlias}",
        description = "This method enables to rotate a current wrapping key with a new wrapping key.",
        responseDescription = "Key rotation response",
    )
    fun rotateWrappingKey(
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
