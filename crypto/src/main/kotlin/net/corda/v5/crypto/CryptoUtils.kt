@file:JvmName("CryptoUtils")

package net.corda.v5.crypto

import net.corda.v5.base.util.EncodingUtils.toBase58
import java.io.InputStream
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE

/**
 * Constant specifying the HMAC SHA-256 algorithm.
 */
const val HMAC_SHA256_ALGORITHM = "HmacSHA256"

/**
 * Constant specifying the HMAC SHA-512 algorithm.
 */
const val HMAC_SHA512_ALGORITHM = "HmacSHA512"

/**
 * Constant specifying the maximum number of key lookup by id items.
 */
const val KEY_LOOKUP_INPUT_ITEMS_LIMIT = 20

/**
 * Constant specifying the maximum number of children keys in the [CompositeKey].
 */
const val COMPOSITE_KEY_CHILDREN_LIMIT = 10

private fun messageDigestSha256(): MessageDigest =
    MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)

/**
 * Calculates SHA256 digest of the given byte array.
 */
fun ByteArray.sha256Bytes(): ByteArray = messageDigestSha256().digest(this)

/**
 * Calculates SHA256 digest of the given [PublicKey]. The function gets the key in its encoded format
 * and then calculates SHA256.
 */
fun PublicKey.sha256Bytes(): ByteArray = messageDigestSha256().digest(encoded)

/** Render a public key to its hash (in Base58) of its serialised form using the DL prefix. */
fun PublicKey.toStringShort(): String = "DL" + toBase58(sha256Bytes())

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
    while(true) {
        val read = this.read(buffer)
        if(read <= 0) break
        mac.update(buffer, 0, read)
    }
    return mac.doFinal()
}

/**
 * Return a [Set] of the contained leaf keys if this is a [CompositeKey].
 * Otherwise, return a [Set] with a single element (this [PublicKey]).
 * <i>Note that leaf keys cannot be of type [CompositeKey].</i>
 */
val PublicKey.keys: Set<PublicKey> get() = (this as? CompositeKey)?.leafKeys ?: setOf(this)

/** Return true if [otherKey] fulfils the requirements of this [PublicKey]. */
fun PublicKey.isFulfilledBy(otherKey: PublicKey): Boolean = isFulfilledBy(setOf(otherKey))

/** Return true if [otherKeys] fulfil the requirements of this [PublicKey]. */
fun PublicKey.isFulfilledBy(otherKeys: Iterable<PublicKey>): Boolean =
    (this as? CompositeKey)?.isFulfilledBy(otherKeys) ?: (this in otherKeys)

/**
 * Checks whether any of the given [keys] match a leaf on the [CompositeKey] tree or a single [PublicKey].
 *
 * <i>Note that this function checks against leaves, which cannot be of type [CompositeKey]. Due to that, if any of the
 * [otherKeys] is a [CompositeKey], this function will not find a match.</i>
 */
fun PublicKey.containsAny(otherKeys: Iterable<PublicKey>): Boolean {
    return if (this is CompositeKey) {
        // `keys` is exported to a standalone value here to make sure it is not evaluated in every loop tick
        val currentKeys = keys
        otherKeys.any { currentKeys.contains(it) }
    } else this in otherKeys
}

/** Returns the set of all [PublicKey]s of the signatures. */
fun Iterable<DigitalSignature.WithKey>.byKeys() = map { it.by }.toSet()

/**
 * Allows Kotlin destructuring, the [PrivateKey] of this [KeyPair].
 * ```kotlin
 * val (private, public) = keyPair
 * ```
 */
operator fun KeyPair.component1(): PrivateKey = this.private

/**
 * Allows Kotlin destructuring, the [PublicKey] of this [KeyPair].
 * ```kotlin
 * val (private, public) = keyPair
 * ```
 */
operator fun KeyPair.component2(): PublicKey = this.public
