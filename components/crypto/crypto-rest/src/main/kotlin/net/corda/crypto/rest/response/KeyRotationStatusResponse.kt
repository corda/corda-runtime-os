package net.corda.crypto.rest.response

import java.time.Instant

/**
 * The result of a key rotation status request.
 *
 * @param tenantId Can either be a holding identity ID, the value 'master' for master wrapping key, or the value
 *      'p2p' for a cluster-level tenant of the P2P services.
 * @param status Overall status of the key rotation. Either In Progress or Done.
 * @param rotationInitiatedTimestamp Timestamp of when the key rotation request was received.
 * @param lastUpdatedTimestamp The last updated timestamp.
 * @param rotatedKeyStatus Number of keys needs rotating grouped by tenantId or wrapping key.
 */

data class KeyRotationStatusResponse(
    val tenantId: String,
    val status: String,
    val rotationInitiatedTimestamp: Instant,
    val lastUpdatedTimestamp: Instant,
    val rotatedKeyStatus: List<Pair<String, RotatedKeysStatus>>,
)

/**
 * The key rotation status for wrapping or signing key.
 *
 * @param total Total number of keys that will be rotated.
 * @param rotatedKeys The number of keys already rotated.
 */

data class RotatedKeysStatus(
    val total: Int,
    val rotatedKeys: Int,
)
