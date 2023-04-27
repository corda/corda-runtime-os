package net.corda.crypto.core

import net.corda.base.internal.OpaqueBytes
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SecureHash.DELIMITER
import java.nio.ByteBuffer

@CordaSerializable
class SecureHashImpl(
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

fun parseDigestAlgoName(algoNameAndHexString: String): String {
    val idx = algoNameAndHexString.indexOf(DELIMITER)
    if (idx == -1) {
        throw IllegalArgumentException("Provided string: $algoNameAndHexString should be of format algorithm:hexadecimal")
    }
    return algoNameAndHexString.substring(0, idx)
}

fun parseSecureHash(algoNameAndHexString: String, expectedLength: Int? = null): SecureHash {
    val idx = algoNameAndHexString.indexOf(DELIMITER)
    return if (idx == -1) {
        throw IllegalArgumentException("Provided string: $algoNameAndHexString should be of format algorithm:hexadecimal")
    } else {
        val algorithm = algoNameAndHexString.substring(0, idx)
        val value = algoNameAndHexString.substring(idx + 1)
        expectedLength?.let {
            require(it == value.length) {
                "Required hex string length: $it for algo: \"$algorithm\" is not met by the provided hex string: \"$value\""
            }
        }
        val data = ByteArrays.parseAsHex(value)
        SecureHashImpl(algorithm, data)
    }
}

val SecureHash.bytes: ByteArray
    get() =
        (this as? SecureHashImpl)?.getBytes()
            ?: throw IllegalArgumentException(
                "User defined subtypes of ${SecureHash::class.java.simpleName} are not permitted"
            )