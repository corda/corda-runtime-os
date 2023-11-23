package net.corda.crypto.rest.response

/**
 * The result of a key rotation request.
 *
 * @param requestId The unique ID for the key rotation start request.
 * @param oldKeyAlias Alias of the key to be rotated.
 * @param newKeyAlias Alias of the new key the [oldKeyAlias] key will be rotated with.
 */

data class KeyRotationResponse(
    val requestId: String,
    val oldKeyAlias: String,
    val newKeyAlias: String,
)
