package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedSecret
import net.corda.v5.crypto.HMAC_SHA256_ALGORITHM
import net.corda.v5.crypto.HMAC_SHA512_ALGORITHM
import net.corda.v5.crypto.hmac
import java.io.InputStream

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
fun ManagedSecret.hmacOf(data: ByteArray, algorithm: String = HMAC_DEFAULT_ALGORITHM): ByteArray =
    data.hmac(secret, algorithm)

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
fun ManagedSecret.hmacOf(inputStream : InputStream, algorithm: String= HMAC_DEFAULT_ALGORITHM): ByteArray =
    inputStream.hmac(secret, algorithm)