package net.corda.libs.packaging

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.jar.JarEntry
import java.util.jar.Manifest
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.internal.FormatVersionReader

internal val secureHashComparator = Comparator.nullsFirst(
    Comparator.comparing(SecureHash::algorithm)
        .then { h1, h2 -> Arrays.compare(h1?.bytes, h2?.bytes) })

/**
 * Compute the [SecureHash] of a [ByteArray] using the specified [DigestAlgorithmName]
 */
fun ByteArray.hash(algo : DigestAlgorithmName = DigestAlgorithmName.DEFAULT_ALGORITHM_NAME) : SecureHash {
    val md = MessageDigest.getInstance(algo.name)
    md.update(this)
    return SecureHash(algo.name, md.digest())
}

fun InputStream.hash(algo : DigestAlgorithmName = DigestAlgorithmName.DEFAULT_ALGORITHM_NAME,
                     buffer: ByteArray = ByteArray(DEFAULT_BUFFER_SIZE)
): SecureHash {
    val md = MessageDigest.getInstance(algo.name)
    DigestInputStream(this, md).use {
        while (it.read(buffer) != -1) {
            // Consume whole stream
        }
    }
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

internal fun Sequence<SecureHash>.summaryHash() : SecureHash? {
    var counter = 0
    return hash {
        this.onEach { ++counter }
            .sortedWith(secureHashComparator)
            .map(SecureHash::toString)
            .map(String::toByteArray)
            .forEach(it::update)
    }.takeIf { counter > 0 }
}

fun Sequence<Certificate>.signerSummaryHash(): SecureHash? =
    map {
        require(it is X509Certificate) {
            "Certificate should be of type ${X509Certificate::class.java.name}"
        }
        it
    }.map {
        MemberX500Name.parse(it.subjectX500Principal.name).toString().toByteArray().hash()
    }.summaryHash()

private const val DEFAULT_VERIFY_JAR_SIGNATURES_KEY = "net.corda.packaging.jarSignatureVerification"
internal fun jarSignatureVerificationEnabledByDefault() = System.getProperty(DEFAULT_VERIFY_JAR_SIGNATURES_KEY)?.toBoolean() ?: true

fun signerInfo(jarEntry: JarEntry) =
    jarEntry.codeSigners.mapTo(LinkedHashSet()) { (it.signerCertPath.certificates[0]).publicKey }

fun readCpbFormatVersion(manifest: Manifest): CpkFormatVersion =
    FormatVersionReader.readCpbFormatVersion(manifest)
