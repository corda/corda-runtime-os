package net.corda.crypto.rest

import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.crypto.rest.response.KeyRotationStatusResponse
import net.corda.crypto.rest.response.ManagedKeyRotationResponse
import net.corda.crypto.rest.response.ManagedKeyRotationStatusResponse
import net.corda.rest.RestResource
import net.corda.rest.SC_ACCEPTED
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.exception.ForbiddenException
import net.corda.rest.exception.InvalidInputDataException
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
     * The [getUnmanagedKeyRotationStatus] gets the latest key rotation status for [keyAlias] if one exists.
     *
     * @return A list of vNodes with the total number of keys needs re-rewrapping and the number of already re-wrapped
     *          keys.
     *
     * @throws ResourceNotFoundException If no key rotation was in progress for [keyAlias].
     */
    @HttpGET(
        path = "rotation/master/{keyAlias}",
        description = "This method gets the status of the latest key rotation.",
        responseDescription = "Number of wrapping keys needs rotating grouped by vNode.",
    )
    fun getUnmanagedKeyRotationStatus(
        @RestPathParameter(description = "The keyAlias we are rotating away from.")
        keyAlias: String
    ): KeyRotationStatusResponse

    /**
     * Initiates the key rotation process.
     * It assumes new default master key alias was set in the crypto config.
     * All wrapping keys which key material is not wrapped with the default master key, will be re-wrapped with this key.
     *
     * @return Key rotation response where the requestId is the unique ID for the key rotation start request.
     *
     * @throws ServiceUnavailableException If the underlying service for sending messages is not available.
     * @throws ForbiddenException If the same key rotation is already in progress.
     * @throws InvalidInputDataException If the input parameters are invalid.
     */

    @HttpPOST(
        path = "rotation/master",
        description = "This method enables to rotate a current master wrapping key with a new master wrapping key.",
        responseDescription = "Key rotation response",
        successCode = SC_ACCEPTED,
    )
    fun startUnmanagedKeyRotation(): ResponseEntity<KeyRotationResponse>

    /**
     * The [getManagedKeyRotationStatus] gets the latest key rotation status for [tenantId] if one exists.
     *
     * @return A list of wrapping keys with the total number of signing keys needs re-wrapping
     *        and the number of already re-wrapped keys.
     *
     */
    @HttpGET(
        path = "rotation/{tenantId}",
        description = "This method gets the status of the latest key rotation for [tenantId].",
        responseDescription = "Number of signing keys which need rotating grouped by tenantId's wrapping keys",
    )
    fun getManagedKeyRotationStatus(
        @RestPathParameter(description = "The tenantId whose wrapping keys are rotating.")
        tenantId: String
    ): ManagedKeyRotationStatusResponse

    /**
     * Initiates the managed key rotation process.
     *
     * @param tenantId UUID of the virtual node.
     *
     * @throws ServiceUnavailableException If the underlying service for sending messages is not available.
     * @throws InvalidInputDataException If the input parameter is invalid.
     *
     */
    @HttpPOST(
        path = "rotation/{tenantId}",
        description = "This method enables to rotate all wrapping keys for tenantId.",
        responseDescription = "Key rotation response",
        successCode = SC_ACCEPTED,
    )
    fun startManagedKeyRotation(
        @RestPathParameter(
            description = "The tenantId whose wrapping keys are requested to be rotated."
        )
        tenantId: String
    ): ResponseEntity<ManagedKeyRotationResponse>
}
