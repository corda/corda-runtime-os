package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Table
import javax.persistence.Version

/**
 * Cpk Metadata Entity without binary data
 */
@Entity
@Table(name = "cpk_metadata", schema = DbSchema.CONFIG)
data class CpkMetadataEntity(
    @EmbeddedId
    val id: CpkKey,
    @Column(name = "file_checksum", nullable = false, unique = true)
    var cpkFileChecksum: String,
    @Column(name = "format_version", nullable = false)
    var formatVersion: String,
    @Column(name = "metadata", nullable = false)
    var serializedMetadata: String,
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0
) {
    /**
     * We'll override to only use the primary key as the default equals causes issues when converting a stream to a list
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CpkMetadataEntity) return false

        if (id != other.id) return false

        return true
    }

    /**
     * We'll override to only use the primary key as the default equals causes issues when converting a stream to a list
     */
    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/**
 * Composite primary key for a Cpk.
 */
@Embeddable
data class CpkKey(
    @Column(name = "cpk_name")
    var cpkName: String,
    @Column(name = "cpk_version")
    var cpkVersion: String,
    @Column(name = "cpk_signer_summary_hash")
    var cpkSignerSummaryHash: String,
) : Serializable
