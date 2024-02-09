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
    description = "Contains operations related to rotation of the master, cluster-level and vNode wrapping keys.",
    path = "wrappingkey",
    minVersion = RestApiVersion.C5_2
)
interface KeyRotationRestResource : RestResource {
    /**
     * The [getKeyRotationStatus] gets the latest key rotation status for [tenantId] if one exists.
     *
     * @param tenantId Can either be a holding identity ID, the value 'master' for master wrapping key or one of the
     *       values 'p2p', 'rest', 'crypto' for corresponding cluster-level services.
     *
     * @return Total number of keys which need rotating grouped by tenantId or tenantId's wrapping keys and
     *      the number of already re-wrapped keys.
     *
     * @throws ResourceNotFoundException If no key rotation was in progress for [tenantId].
     */
    @HttpGET(
        path = "rotation/{tenantId}",
        description = "This method gets the status of the latest key rotation for [tenantId].",
        responseDescription = "Number of keys which need rotating grouped by tenantId or tenantId's wrapping keys.",
    )
    fun getKeyRotationStatus(
        @RestPathParameter(description = "Can either be a holding identity ID, the value 'master' for master wrapping " +
                "key or one of the values 'p2p', 'rest', 'crypto' for corresponding cluster-level services.")
        tenantId: String
    ): KeyRotationStatusResponse

    /**
     * Initiates a master wrapping key, a cluster-level tenant wrapping key or a vNode wrapping key rotation process.
     *
     * @param tenantId Can either be a holding identity ID, the value 'master' for master wrapping key or one of the
     *       values 'p2p', 'rest', 'crypto' for corresponding cluster-level services.
     *
     * @return Key rotation response where the requestId is the unique ID for the key rotation start request.
     *
     * @throws ServiceUnavailableException If the underlying service for sending messages is not available.
     * @throws InvalidInputDataException If the input parameter is invalid.
     *
     */
    @HttpPOST(
        path = "rotation/{tenantId}",
        description = "This method enables to rotate master wrapping key or all wrapping keys for tenantId " +
                "(holding identity ID or cluster-level tenant).",
        responseDescription = "Key rotation response",
        successCode = SC_ACCEPTED,
    )
    fun startKeyRotation(
        @RestPathParameter(description = "Can either be a holding identity ID, the value 'master' for master wrapping " +
                "key or one of the values 'p2p', 'rest', 'crypto' for corresponding cluster-level services.")
        tenantId: String
    ): ResponseEntity<KeyRotationResponse>
}
