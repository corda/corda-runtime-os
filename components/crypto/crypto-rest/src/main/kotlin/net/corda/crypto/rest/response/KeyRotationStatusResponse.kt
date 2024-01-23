package net.corda.crypto.rest.response

import java.time.Instant

/**
 * The result of a key rotation status request.
 *
 * @param rootKeyAlias Alias of the key to be rotated.
 * @param status Overall status of the key rotation. Either In Progress or Done.
 * @param lastUpdatedTimestamp The last updated timestamp.
 * @param wrappingKeys Number of wrapping keys needs rotating grouped by tenantId.
 */

data class KeyRotationStatusResponse(
    val rootKeyAlias: String,
    val status: String,
    val lastUpdatedTimestamp: Instant,
    val wrappingKeys: List<Pair<String, TenantIdWrappingKeysStatus>>,
)

/**
 * The key rotation status for wrapping keys per particular tenantId.
 *
 * @param total Total number of wrapping keys that will be rotated for particular tenantId.
 * @param rotatedKeys The number of wrapping keys already rotated for particular tenantId.
 */

data class TenantIdWrappingKeysStatus(
    val total: Int,
    val rotatedKeys: Int,
)