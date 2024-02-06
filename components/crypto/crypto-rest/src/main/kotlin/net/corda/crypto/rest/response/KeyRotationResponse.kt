package net.corda.crypto.rest.response

/**
 * The result of a key rotation request.
 *
 * @param requestId The unique ID for the key rotation start request.
 * @param tenantId The tenantId.
 */

data class KeyRotationResponse(
    val requestId: String,
    val tenantId: String,
)
