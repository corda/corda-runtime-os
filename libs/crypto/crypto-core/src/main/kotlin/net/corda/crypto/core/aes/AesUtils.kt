package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedSecret
import net.corda.v5.crypto.MessageAuthenticationCode
import java.io.InputStream
import net.corda.crypto.utils.hmac

const val HMAC_DEFAULT_ALGORITHM = MessageAuthenticationCode.HMAC_SHA256_ALGORITHM

/**
 * Calculates HMAC of the [data] [ByteArray] using SHA256.
 */
fun ManagedSecret.hmac256Of(data: ByteArray): ByteArray = hmacOf(data, MessageAuthenticationCode.HMAC_SHA256_ALGORITHM)

/**
 * Calculates HMAC of the [data] [ByteArray] using SHA512.
 */
fun ManagedSecret.hmac512Of(data: ByteArray): ByteArray = hmacOf(data, MessageAuthenticationCode.HMAC_SHA512_ALGORITHM)

/**
 * Calculates HMAC of the [data] [ByteArray] using provided algorithm.
 */
fun ManagedSecret.hmacOf(data: ByteArray, algorithm: String = HMAC_DEFAULT_ALGORITHM): ByteArray =
    data.hmac(secret, algorithm)

/**
 * Calculates HMAC of the [inputStream] [InputStream] using SHA256.
 */
fun ManagedSecret.hmac256Of(inputStream: InputStream): ByteArray =
    hmacOf(inputStream, MessageAuthenticationCode.HMAC_SHA256_ALGORITHM)

/**
 * Calculates HMAC of the [inputStream] [InputStream] using SHA512.
 */
fun ManagedSecret.hmac512Of(inputStream: InputStream): ByteArray =
    hmacOf(inputStream, MessageAuthenticationCode.HMAC_SHA512_ALGORITHM)

/**
 * Calculates HMAC of the [inputStream] [InputStream] using provided algorithm.
 */
fun ManagedSecret.hmacOf(inputStream : InputStream, algorithm: String= HMAC_DEFAULT_ALGORITHM): ByteArray =
    inputStream.hmac(secret, algorithm)