package net.corda.libs.cpi.datamodel

import net.corda.v5.crypto.SecureHash

data class CpkFile(val fileChecksum: SecureHash, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkFile

        if (fileChecksum != other.fileChecksum) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileChecksum.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}