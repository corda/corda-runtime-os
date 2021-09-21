package net.corda.packaging.internal

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

/**
 * Compute the [SecureHash] of a [ByteArray] using the specified [DigestAlgorithmName]
 */
internal fun ByteArray.hash(algo : DigestAlgorithmName = DigestAlgorithmName.DEFAULT_ALGORITHM_NAME) : SecureHash {
    val md = MessageDigest.getInstance(algo.name)
    md.update(this)
    return SecureHash(algo.name, md.digest())
}

/**
 * Creates a [SecureHash] instance with the specified [algorithm]
 * @param algorithm the hash algorithm to be used
 * @param withDigestAction a callback that receives a [MessageDigest] instance and is responsible to call
 * [MessageDigest.update] with the data that needs to be hashed
 * @return the resulting [SecureHash]
 */
internal inline fun hash(algorithm : DigestAlgorithmName = DigestAlgorithmName.DEFAULT_ALGORITHM_NAME, withDigestAction : (MessageDigest) -> Unit) : SecureHash {
    val md = MessageDigest.getInstance(algorithm.name)
    withDigestAction(md)
    return SecureHash(algorithm.name, md.digest())
}