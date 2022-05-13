package net.corda.chunking.datamodel

import net.corda.db.schema.DbSchema
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.OneToMany
import javax.persistence.Table

/**
 * This entity uses a composite primary key
 */
@Entity
@IdClass(ChunkEntityPrimaryKey::class)
@Table(name = "file_upload", schema = DbSchema.CONFIG)
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
    var data: ByteArray?,
    @OneToMany(mappedBy = "chunk", orphanRemoval = true, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var chunkProperties: MutableSet<ChunkPropertyEntity> = mutableSetOf()
) {
    @Suppress("ComplexMethod")
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
        if (chunkProperties != other.chunkProperties) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (checksum?.hashCode() ?: 0)
        result = 31 * result + partNumber
        result = 31 * result + offset.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + chunkProperties.hashCode()
        return result
    }

    override fun toString(): String {
        return this::class.simpleName + "(requestId = $requestId , fileName = $fileName , " +
                "partNumber = $partNumber , chunkProperties = $chunkProperties )"
    }
}
