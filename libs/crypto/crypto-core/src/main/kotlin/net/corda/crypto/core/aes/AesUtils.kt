package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedSecret
import net.corda.crypto.core.ManagedSecret.Companion.HMAC_ALGORITHM
import java.io.InputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE

/**
 * Calculates HMAC of the [data] [ByteArray].
 */
fun ManagedSecret.hmacOf(data: ByteArray, algorithm: String = HMAC_ALGORITHM): ByteArray {
    val secretKeySpec = SecretKeySpec(secret, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)
    return mac.doFinal(data)
}

/**
 * Calculates HMAC of the [inputStream] [InputStream].
 */
fun ManagedSecret.hmacOf(inputStream : InputStream, algorithm: String = HMAC_ALGORITHM): ByteArray {
    val secretKeySpec = SecretKeySpec(secret, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    while(true) {
        val read = inputStream.read(buffer)
        if(read <= 0) break
        mac.update(buffer, 0, read)
    }
    return mac.doFinal()
}