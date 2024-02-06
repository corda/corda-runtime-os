package net.corda.crypto.rest.response

/**
 * The result of a key rotation request.
 *
 * @param requestId The unique ID for the key rotation start request.
 */

data class KeyRotationResponse(
    val requestId: String,
)
