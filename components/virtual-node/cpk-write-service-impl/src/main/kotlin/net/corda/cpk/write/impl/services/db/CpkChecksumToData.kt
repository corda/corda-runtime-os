package net.corda.cpk.write.impl.services.db

import net.corda.v5.crypto.SecureHash

data class CpkChecksumToData(
    val checksum: SecureHash,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkChecksumToData

        if (checksum != other.checksum) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = checksum.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}