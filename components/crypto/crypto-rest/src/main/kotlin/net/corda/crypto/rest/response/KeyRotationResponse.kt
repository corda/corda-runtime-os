package net.corda.crypto.rest.response

import java.time.Duration

/**
 * The result of a rotation request.
 *
 * @param requestId The unique ID for the key rotation start request.
 * @param processedCount The number of keys that finished rotating.
 * @param duration The time it took to rotate the keys in [processedCount].
 * @param expectedTotal The number of keys yet to be rotated.
 */

data class KeyRotationResponse(
    val requestId: String,
    val processedCount: Int,
    val duration: Duration,
    val expectedTotal: Int
)
