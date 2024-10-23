package net.corda.ledger.libs.uniqueness

import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SecureHash.DELIMITER

data class UniquenessTestSecureHashImpl(
    val algo: String,
    val bytes: ByteArray
) : SecureHash {
    override fun getAlgorithm(): String {
        return algo
    }

    override fun toHexString(): String {
        return ByteArrays.toHexString(bytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquenessTestSecureHashImpl) return false

        if (algo != other.algo) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = algo.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString() = "$algorithm$DELIMITER${toHexString()}"
}

class UniquenessSecureHashFactoryTestImpl : UniquenessSecureHashFactory {
    override fun createSecureHash(algorithm: String, bytes: ByteArray): SecureHash {
        return UniquenessTestSecureHashImpl(algorithm, bytes)
    }

    override fun getBytes(hash: SecureHash): ByteArray {
        return (hash as UniquenessTestSecureHashImpl).bytes
    }

    override fun parseSecureHash(hashString: String): SecureHash {
        val idx = hashString.indexOf(DELIMITER)
        val algoName = hashString.substring(0, idx)
        val bytes = ByteArrays.parseAsHex(hashString.substring(algoName.length + 1))
        return UniquenessTestSecureHashImpl(
            algoName,
            bytes
        )
    }
}
