package net.corda.ledger.libs.uniqueness.data

import net.corda.base.internal.OpaqueBytes
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SecureHash.DELIMITER
import java.nio.ByteBuffer
import java.security.MessageDigest

// TODO This is just a 1-to-1 copy of `SecureHashImpl` and renamed to avoid confusion
@CordaSerializable
class UniquenessSecureHashImpl(
    private val algorithm: String,
    bytes: ByteArray
) : SecureHash, OpaqueBytes(bytes) {

    override fun getAlgorithm() = algorithm

    override fun toHexString() = ByteArrays.toHexString(bytes)

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is SecureHash -> false
            else -> algorithm == other.algorithm && super.equals(other)
        }
    }

    override fun hashCode() = ByteBuffer.wrap(bytes).int

    override fun toString() = "$algorithm$DELIMITER${toHexString()}"
}

fun parseSecureHashAlgoName(algoNameAndHexString: String): String {
    val idx = algoNameAndHexString.indexOf(DELIMITER)
    if (idx == -1) {
        throw IllegalArgumentException("Provided string: $algoNameAndHexString should be of format algorithm:hexadecimal")
    }
    return algoNameAndHexString.substring(0, idx)
}

fun parseSecureHashHexString(algoNameAndHexString: String): String {
    val algoName = parseSecureHashAlgoName(algoNameAndHexString)
    return algoNameAndHexString.substring(algoName.length + 1)
}

fun parseSecureHash(algoNameAndHexString: String): SecureHash {
    val algoName = parseSecureHashAlgoName(algoNameAndHexString)
    val hexString = algoNameAndHexString.substring(algoName.length + 1)
    return UniquenessSecureHashImpl(algoName, ByteArrays.parseAsHex(hexString))
}

val SecureHash.bytes: ByteArray
    get() =
        (this as? UniquenessSecureHashImpl)?.getBytes()
            ?: throw IllegalArgumentException(
                "User defined subtypes of ${SecureHash::class.java.simpleName} are not permitted"
            )

/**
 * Returns a random set of bytes
 */
fun randomBytes(): ByteArray {
    return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
}

/**
 * Returns a random secure hash of the specified algorithm
 */
fun randomUniquenessSecureHash(algorithm: String = "SHA-256"): SecureHash {
    val digest = MessageDigest.getInstance(algorithm)
    return UniquenessSecureHashImpl(digest.algorithm, digest.digest(randomBytes()))
}
