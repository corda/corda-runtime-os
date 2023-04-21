package net.corda.libs.cpi.datamodel

import net.corda.v5.crypto.SecureHash

data class CpkFile(val fileChecksum: SecureHash, val data: ByteArray) {

    companion object {
        private val VERSION_NOT_SET = -1
    }

    // This field should only be set by the CpkFileRepository when the entity is converted to a dto
    // If the field is not set then it was not created by the CpkFileRepository.
    var version: Int = VERSION_NOT_SET
            private set

    // This constructor is internal to ensure people won't set the version field. That should only be
    // done by the CpkFileRepository.
    internal constructor(fileChecksum: SecureHash, data: ByteArray, version: Int) :
            this(fileChecksum, data) {
        this.version = version
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkFile

        if (fileChecksum != other.fileChecksum) return false
        if (!data.contentEquals(other.data)) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileChecksum.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + version
        return result
    }
}
