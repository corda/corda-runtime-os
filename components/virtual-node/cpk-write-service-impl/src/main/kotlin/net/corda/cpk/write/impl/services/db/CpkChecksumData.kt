package net.corda.cpk.write.impl.services.db

import net.corda.v5.crypto.SecureHash

data class CpkChecksumData(
    val checksum: SecureHash,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkChecksumData

        if (checksum != other.checksum) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = checksum.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}