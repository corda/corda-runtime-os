package net.corda.crypto.rest

import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.crypto.rest.response.KeyRotationStatusResponse
import net.corda.rest.RestResource
import net.corda.rest.SC_ACCEPTED
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.rest.response.ResponseEntity

/**
 * Key Rotation API consists of endpoints to request a key rotation operation and to check the status of such operation.
 */
@HttpRestResource(
    name = "Key Rotation API",
    description = "Contains operations related to rotation of the master and vNode wrapping keys.",
    path = "wrappingkey",
    minVersion = RestApiVersion.C5_2
)
interface KeyRotationRestResource : RestResource {
    /**
     * The [getKeyRotationStatus] gets the latest key rotation status for [tenantId] if one exists.
     *
     * @param tenantId UUID of the virtual node or 'master' for master wrapping key rotation status.
     *
     * @return Total number of keys which need rotating grouped by tenantId or tenantId's wrapping keys and
     *      the number of already re-wrapped keys.
     *
     * @throws ResourceNotFoundException If no key rotation was in progress for [tenantId].
     */
    @HttpGET(
        path = "rotation/{tenantId}",
        description = "This method gets the status of the latest key rotation for [tenantId].",
        responseDescription = "Number of keys which need rotating grouped by tenantId or tenantId's wrapping keys",
    )
    fun getKeyRotationStatus(
        @RestPathParameter(description = "UUID of the virtual node or 'master' for master wrapping key rotation status.")
        tenantId: String
    ): KeyRotationStatusResponse

    /**
     * Initiates the master wrapping key or vNode wrapping key rotation process.
     *
     * @param tenantId UUID of the virtual node or 'master' for master wrapping key rotation.
     *
     * @return Key rotation response where the requestId is the unique ID for the key rotation start request.
     *
     * @throws ServiceUnavailableException If the underlying service for sending messages is not available.
     * @throws InvalidInputDataException If the input parameter is invalid.
     *
     */
    @HttpPOST(
        path = "rotation/{tenantId}",
        description = "This method enables to rotate all wrapping keys for tenantId or master wrapping key.",
        responseDescription = "Key rotation response",
        successCode = SC_ACCEPTED,
    )
    fun startKeyRotation(
        @RestPathParameter(
            description = "UUID of the virtual node or 'master' for master wrapping key rotation."
        )
        tenantId: String
    ): ResponseEntity<KeyRotationResponse>
}
