package net.corda.cpk.write.types

import net.corda.db.schema.DbSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "cpk", schema = DbSchema.CONFIG)
data class CpkEntity(
    @Id
    @Column(name = "file_checksum", nullable = false)
    val fileChecksum: String,
    @Column(name = "data", nullable = false)
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkEntity

        if (fileChecksum != other.fileChecksum) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileChecksum.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}