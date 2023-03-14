package net.corda.crypto.core

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.ByteArrays
import net.corda.v5.base.types.OpaqueBytes
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

fun parseSecureHash(algoNameAndHexString: String): SecureHash {
    val idx = algoNameAndHexString.indexOf(DELIMITER)
    return if (idx == -1) {
        throw IllegalArgumentException("Provided string: $algoNameAndHexString should be of format algorithm:hexadecimal")
    } else {
        val algorithm = algoNameAndHexString.substring(0, idx)
        val value = algoNameAndHexString.substring(idx + 1)
        val data = ByteArrays.parseAsHex(value)
        SecureHashImpl(algorithm, data)
    }
}