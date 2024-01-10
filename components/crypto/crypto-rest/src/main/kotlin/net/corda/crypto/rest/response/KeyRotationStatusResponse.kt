package net.corda.crypto.rest.response

/**
 * The result of a key rotation request.
 *
 * @param oldKeyAlias Alias of the key to be rotated.
 */

data class KeyRotationStatusResponse(
    val oldKeyAlias: String,
    val wrappingKeys: List<Pair<String, TenantIdWrappingKeysStatus>>,
)

data class TenantIdWrappingKeysStatus(
    val total: Int,
    val rotatedKeys: Int,
)