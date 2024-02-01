package net.corda.crypto.rest.response

/**
 * The result of a managed key rotation request.
 *
 * @param requestId The unique ID for the key rotation start request.
 * @param tenantId The tenantId.
 */

data class ManagedKeyRotationResponse(
    val requestId: String,
    val tenantId: String,
)
