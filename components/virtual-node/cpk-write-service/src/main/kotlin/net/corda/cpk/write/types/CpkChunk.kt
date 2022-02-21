package net.corda.cpk.write.types

data class CpkChunk(
    val id: CpkChunkId,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkChunk

        if (id != other.id) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}