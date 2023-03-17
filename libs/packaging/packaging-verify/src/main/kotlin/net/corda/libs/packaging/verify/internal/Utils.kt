package net.corda.libs.packaging.verify.internal

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.hash
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.security.CodeSigner
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.Arrays
import java.util.jar.Manifest

internal fun Manifest.requireAttribute(name: String) {
    if (mainAttributes.getValue(name) == null)
        throw CordappManifestException("Manifest is missing required attribute \"$name\"")
}

internal fun Manifest.requireAttributeValueIn(name: String, vararg values: String?) {
    with (mainAttributes.getValue(name)) {
        if (this !in values)
            throw CordappManifestException("Manifest has invalid attribute \"$name\" value \"$this\"")
    }
}

/**
 * Creates a [SecureHash] instance with the specified [algorithm]
 * @param algorithm the hash algorithm to be used
 * @param withDigestAction a callback that receives a [MessageDigest] instance and is responsible to call
 * [MessageDigest.update] with the data that needs to be hashed
 * @return the resulting [SecureHash]
 */
internal inline fun hash(
    algorithm : DigestAlgorithmName = DigestAlgorithmName.SHA2_256,
    withDigestAction : (MessageDigest) -> Unit
) : SecureHash {
    val md = MessageDigest.getInstance(algorithm.name)
    withDigestAction(md)
    return SecureHashImpl(algorithm.name, md.digest())
}

internal val secureHashComparator = Comparator.nullsFirst(
    Comparator.comparing(SecureHash::getAlgorithm)
        .then { h1, h2 -> Arrays.compare(h1?.bytes, h2?.bytes) })

internal fun Sequence<SecureHash>.sortedSequenceHash() : SecureHash {
    return hash {
        this.sortedWith(secureHashComparator)
            .map(SecureHash::toString)
            .map(String::toByteArray)
            .forEach(it::update)
    }
}

internal fun Sequence<Certificate>.certSequenceHash() : SecureHash =
    map { it.publicKey.encoded.hash() }.sortedSequenceHash()

internal fun List<CodeSigner>.codeSignersHash() =
    mapTo(HashSet()) { it.signerCertPath.certificates.first() }
        .asSequence().certSequenceHash()

internal fun hash(inputStream: InputStream, algorithm: String): SecureHash {
    val digest = MessageDigest.getInstance(algorithm)
    DigestInputStream(inputStream, digest).use {
        it.readAllBytes()
    }
    return SecureHashImpl(algorithm, digest.digest())
}

internal fun <T> List<T>.firstOrThrow(noElementsException: Exception): T {
    if (isEmpty())
        throw noElementsException
    return this[0]
}

internal fun <T> List<T>.singleOrThrow(noElementsException: Exception, multipleElementsException: Exception): T {
    return when (size) {
        0 -> throw noElementsException
        1 -> this[0]
        else -> throw multipleElementsException
    }
}
