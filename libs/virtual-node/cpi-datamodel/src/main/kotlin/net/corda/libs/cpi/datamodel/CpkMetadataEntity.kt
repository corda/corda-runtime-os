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
    val cpkFileChecksum: String,
    @Column(name = "format_version", nullable = false)
    val formatVersion: String,
    @Column(name = "metadata", nullable = false)
    val serializedMetadata: String,
    @Column(name = "is_deleted", nullable = false)
    val isDeleted: Boolean = false,
    @Version
    @Column(name = "entity_version", nullable = false)
    val entityVersion: Int = 0
) : Serializable


/**
 * Composite primary key for a Cpk.
 */
@Embeddable
data class CpkKey(
    @Column(name = "cpk_name")
    val cpkName: String,
    @Column(name = "cpk_version")
    val cpkVersion: String,
    @Column(name = "cpk_signer_summary_hash")
    val cpkSignerSummaryHash: String,
) : Serializable
