package net.corda.libs.cpi.datamodel.entities

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * Append only audit log of changelogs of a CPK.
 */
@Suppress("LongParameterList")
@Entity
@Table(name = "cpk_db_change_log_audit")
class CpkDbChangeLogAuditEntity(
    @Id
    val id: String,
    @Column(name = "cpk_file_checksum", nullable = false, updatable = false)
    val cpkFileChecksum: String,
    @Column(name = "file_path", nullable = false, updatable = false)
    val filePath: String,
    @Column(name = "content", nullable = false, updatable = false)
    val content: String,
    @Column(name = "is_deleted", nullable = false, updatable = false)
    var isDeleted: Boolean = false,
    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkDbChangeLogAuditEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}