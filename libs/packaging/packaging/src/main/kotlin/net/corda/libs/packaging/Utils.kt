package net.corda.libs.packaging

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.Collections.singleton
import java.util.jar.JarEntry
import java.util.jar.Manifest
import javax.naming.ldap.LdapName

internal val secureHashComparator = Comparator.nullsFirst(
    Comparator.comparing(SecureHash::getAlgorithm)
        .then { h1, h2 -> Arrays.compare(h1?.bytes, h2?.bytes) }
)

/**
 * Compute the [SecureHash] of a [ByteArray] using the specified [DigestAlgorithmName]
 */
fun ByteArray.hash(algo: DigestAlgorithmName = DigestAlgorithmName.SHA2_256): SecureHash {
    val md = MessageDigest.getInstance(algo.name)
    md.update(this)
    return SecureHashImpl(algo.name, md.digest())
}

fun InputStream.hash(
    algo: DigestAlgorithmName = DigestAlgorithmName.SHA2_256,
    buffer: ByteArray = ByteArray(DEFAULT_BUFFER_SIZE)
): SecureHash {
    val md = MessageDigest.getInstance(algo.name)
    DigestInputStream(this, md).use {
        while (it.read(buffer) != -1) {
            // Consume whole stream
        }
    }
    return SecureHashImpl(algo.name, md.digest())
}

/**
 * Transfer the entire contents of [input] to this [WritableByteChannel].
 */
@Throws(IOException::class)
fun WritableByteChannel.writeFile(input: FileChannel) {
    var pos = 0L
    var bytesToWrite = input.size()
    while (bytesToWrite > 0) {
        val bytesWritten = input.transferTo(pos, bytesToWrite, this)
        pos += bytesWritten
        bytesToWrite -= bytesWritten
    }
}

private val READ_ONLY = singleton(OWNER_READ)

/**
 * Updates [file] to be read-only. Compatible with both UNIX and Windows.
 */
@Throws(IOException::class)
fun setReadOnly(file: Path) {
    Files.getFileAttributeView(file, PosixFileAttributeView::class.java)?.also { view ->
        view.setPermissions(READ_ONLY)
    } ?: run {
        Files.getFileAttributeView(file, DosFileAttributeView::class.java)?.also { view ->
            view.setReadOnly(true)
        }
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
    algorithm: DigestAlgorithmName = DigestAlgorithmName.SHA2_256,
    withDigestAction: (MessageDigest) -> Unit
): SecureHash {
    val md = MessageDigest.getInstance(algorithm.name)
    withDigestAction(md)
    return SecureHashImpl(algorithm.name, md.digest())
}

internal fun Sequence<SecureHash>.summaryHash(): SecureHash? {
    var counter = 0
    return hash {
        this.onEach { ++counter }
            .sortedWith(secureHashComparator)
            .map(SecureHash::toString)
            .map(String::toByteArray)
            .forEach(it::update)
    }.takeIf { counter > 0 }
}

fun Sequence<Certificate>.signerSummaryHash(): SecureHash {
    val summaryHash = map {
        it as? X509Certificate
            ?: throw IllegalArgumentException("Certificate should be of type ${X509Certificate::class.java.name}")
        // NOTE: this should NOT use MemberX500Name as we don't need/want to apply Corda Member restrictions
        LdapName(it.subjectX500Principal.name).filterSupportedAttributes().toByteArray().hash()
    }.summaryHash()

    return summaryHash
        ?: throw IllegalArgumentException("Summary Hash cannot be null. There must be at least one valid signature")
}

internal val X500_NAME_SUPPORTED_ATTRIBUTES = linkedSetOf("CN", "OU", "O", "L", "ST", "C")

private fun LdapName.filterSupportedAttributes(): String {
    val includedAttributes = rdns.filter {
        it.type in X500_NAME_SUPPORTED_ATTRIBUTES
    }

    val sorted = includedAttributes.sortedWith { rdn1, rdn2 ->
        X500_NAME_SUPPORTED_ATTRIBUTES.indexOf(rdn1.type) -
            X500_NAME_SUPPORTED_ATTRIBUTES.indexOf(rdn2.type)
    }

    return LdapName(sorted).toString()
}

fun Collection<Certificate>.signerSummaryHashForRequiredSigners(): SecureHash {
    require(isNotEmpty()) {
        "Can't create signer summary hash on an empty signers set"
    }
    return asSequence().signerSummaryHash()
}

private const val DEFAULT_VERIFY_JAR_SIGNATURES_KEY = "net.corda.packaging.jarSignatureVerification"
internal fun jarSignatureVerificationEnabledByDefault() = System.getProperty(DEFAULT_VERIFY_JAR_SIGNATURES_KEY)?.toBoolean() ?: true

fun signerInfo(jarEntry: JarEntry) =
    jarEntry.codeSigners.mapTo(LinkedHashSet()) { (it.signerCertPath.certificates[0]).publicKey }

fun readCpbFormatVersion(manifest: Manifest): CpkFormatVersion =
    FormatVersionReader.readCpbFormatVersion(manifest)
