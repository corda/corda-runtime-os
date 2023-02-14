package net.corda.libs.cpi.datamodel.entities

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version

/**
 * Cpk Metadata Entity without binary data
 */
@Entity
@Table(name = "cpk_metadata")
class CpkMetadataEntity(
    @Id
    @Column(name = "file_checksum", nullable = false, unique = true)
    var cpkFileChecksum: String,
    @Column(name = "cpk_name")
    var cpkName: String,
    @Column(name = "cpk_version")
    var cpkVersion: String,
    @Column(name = "cpk_signer_summary_hash")
    var cpkSignerSummaryHash: String,
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

        if (cpkFileChecksum != other.cpkFileChecksum) return false

        return true
    }

    /**
     * We'll override to only use the primary key as the default equals causes issues when converting a stream to a list
     */
    override fun hashCode(): Int {
        return cpkFileChecksum.hashCode()
    }
}
