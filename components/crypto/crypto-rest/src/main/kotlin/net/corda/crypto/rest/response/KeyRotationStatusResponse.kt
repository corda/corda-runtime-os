package net.corda.crypto.rest.response

import java.time.Instant

/**
 * The result of a key rotation status request.
 *
 * @param oldParentKeyAlias Alias of the key to be rotated.
 * @param newParentKeyAlias Alias of the key we are rotating to.
 * @param status Overall status of the key rotation. Either In Progress or Done.
 * @param createdTimestamp Timestamp of then the key rotation request was received.
 * @param lastUpdatedTimestamp The last updated timestamp.
 * @param wrappingKeys Number of wrapping keys needs rotating grouped by tenantId.
 */

data class KeyRotationStatusResponse(
    val oldParentKeyAlias: String,
    val newParentKeyAlias: String,
    val status: String,
    val createdTimestamp: Instant,
    val lastUpdatedTimestamp: Instant,
    val wrappingKeys: List<Pair<String, RotatedKeysStatus>>,
)

/**
 * The result of a managed key rotation status request.
 *
 * @param tenantId TenantId whose wrapping keys are rotating.
 * @param status Overall status of the key rotation. Either In Progress or Done.
 * @param createdTimestamp Timestamp of then the key rotation request was received.
 * @param lastUpdatedTimestamp The last updated timestamp.
 * @param signingKeys Number of signing keys needs rotating grouped by wrapping key.
 */

data class ManagedKeyRotationStatusResponse(
    val tenantId: String,
    val status: String,
    val createdTimestamp: Instant,
    val lastUpdatedTimestamp: Instant,
    val signingKeys: List<Pair<String, RotatedKeysStatus>>,
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
