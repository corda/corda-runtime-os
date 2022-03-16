package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedSecret
import net.corda.crypto.core.ManagedSecret.Companion.HMAC_ALGORITHM
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Calculates HMAC of the [data].
 */
fun ManagedSecret.hmacOf(data: ByteArray, algorithm: String = HMAC_ALGORITHM): ByteArray {
    val secretKeySpec = SecretKeySpec(secret, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)
    return mac.doFinal(data)
}