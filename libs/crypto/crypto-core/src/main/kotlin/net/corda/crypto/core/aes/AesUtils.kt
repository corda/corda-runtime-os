package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedSecret
import java.io.InputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE
const val HMAC_SHA256_ALGORITHM = "HmacSHA256"
const val HMAC_SHA512_ALGORITHM = "HmacSHA512"
const val HMAC_DEFAULT_ALGORITHM = HMAC_SHA256_ALGORITHM

/**
 * Calculates HMAC of the [data] [ByteArray] using SHA256.
 */
fun ManagedSecret.hmac256Of(data: ByteArray): ByteArray = hmacOf(data, HMAC_SHA256_ALGORITHM)

/**
 * Calculates HMAC of the [data] [ByteArray] using SHA512.
 */
fun ManagedSecret.hmac512Of(data: ByteArray): ByteArray = hmacOf(data, HMAC_SHA512_ALGORITHM)

/**
 * Calculates HMAC of the [data] [ByteArray] using provided algorithm.
 */
fun ManagedSecret.hmacOf(data: ByteArray, algorithm: String = HMAC_DEFAULT_ALGORITHM): ByteArray {
    val secretKeySpec = SecretKeySpec(secret, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)
    return mac.doFinal(data)
}

/**
 * Calculates HMAC of the [inputStream] [InputStream] using SHA256.
 */
fun ManagedSecret.hmac256Of(inputStream : InputStream): ByteArray = hmacOf(inputStream, HMAC_SHA256_ALGORITHM)

/**
 * Calculates HMAC of the [inputStream] [InputStream] using SHA512.
 */
fun ManagedSecret.hmac512Of(inputStream : InputStream): ByteArray = hmacOf(inputStream, HMAC_SHA512_ALGORITHM)

/**
 * Calculates HMAC of the [inputStream] [InputStream] using provided algorithm.
 */
fun ManagedSecret.hmacOf(inputStream : InputStream, algorithm: String= HMAC_DEFAULT_ALGORITHM): ByteArray {
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