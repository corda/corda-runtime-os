package net.corda.chunking.datamodel

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * This entity uses a composite primary key
 */
@Entity
@IdClass(ChunkEntityPrimaryKey::class)
@Table(name = "file_upload")
data class ChunkEntity(
    @Id
    @Column(name = "request_id", nullable = false)
    val requestId: String,
    @Column(name = "filename", nullable = true)
    var fileName: String?,
    @Column(name = "checksum", nullable = true)
    var checksum: String?,
    @Id
    @Column(name = "part_nr", nullable = false)
    var partNumber: Int,
    @Column(name = "data_offset", nullable = false)
    var offset: Long,
    @Column(name = "data", nullable = true)
    var data: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChunkEntity

        if (requestId != other.requestId) return false
        if (fileName != other.fileName) return false
        if (checksum != other.checksum) return false
        if (partNumber != other.partNumber) return false
        if (offset != other.offset) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (checksum?.hashCode() ?: 0)
        result = 31 * result + partNumber
        result = 31 * result + offset.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
