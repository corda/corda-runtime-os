package net.corda.crypto.utils

import java.io.InputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE

/**
 * Calculates HMAC using provided secret and algorithm for provided byte array.
 */
fun ByteArray.hmac(secret: ByteArray, algorithm: String): ByteArray {
    val secretKeySpec = SecretKeySpec(secret, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)
    return mac.doFinal(this)
}


/**
 * Calculates HMAC using provided secret and algorithm for provided input stream.
 */
fun InputStream.hmac(secret: ByteArray, algorithm: String): ByteArray {
    val secretKeySpec = SecretKeySpec(secret, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    while (true) {
        val read = this.read(buffer)
        if (read <= 0) break
        mac.update(buffer, 0, read)
    }
    return mac.doFinal()
}