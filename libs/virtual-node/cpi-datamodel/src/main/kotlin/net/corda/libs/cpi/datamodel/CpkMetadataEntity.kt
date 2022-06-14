package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version

/**
 * Cpk Metadata Entity without binary data
 */
@Entity
@Table(name = "cpk_metadata", schema = DbSchema.CONFIG)
data class CpkMetadataEntity(
    @Id
    @Column(name = "file_checksum", nullable = false)
    val cpkFileChecksum: String,
    @Column(name = "cpk_name", nullable = false)
    val cpkName: String,
    @Column(name = "cpk_version", nullable = false)
    val cpkVersion: String,
    @Column(name = "cpk_signer_summary_hash", nullable = false)
    val cpkSignerSummaryHash: String,
    @Column(name = "format_version", nullable = false)
    val formatVersion: String,
    @Column(name = "metadata", nullable = false)
    val serializedMetadata: String,
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
) : Serializable {
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0
}