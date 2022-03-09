package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.OneToMany
import javax.persistence.Table

/**
 * Cpi entity
 *
 * @property name CPI Name
 * @property version CPI Version
 * @property signerSummaryHash CPI Signer Summary Hash
 * @property fileName CPI original filename
 * @property fileChecksum Checksum for the original CPI file
 * @property groupPolicy Group Policy JSON document
 * @property groupId MGM Group ID
 * @property fileUploadRequestId optional request ID for the file upload
 */
@Entity
@Table(name = "cpi", schema = DbSchema.CONFIG)
@IdClass(CpiMetadataEntityKey::class)
data class CpiMetadataEntity(
    @Id
    @Column(name = "name", nullable = false)
    val name: String,
    @Id
    @Column(name = "version", nullable = false)
    val version: String,
    @Id
    @Column(name = "signer_summary_hash", nullable = false)
    val signerSummaryHash: String,
    @Column(name = "id_full_hash", nullable = false)
    val idFullHash: String,
    @Column(name = "id_short_hash", nullable = false)
    val idShortHash: String,
    @Column(name = "file_name", nullable = false)
    val fileName: String,
    @Column(name = "file_checksum", nullable = false)
    val fileChecksum: String,
    @Column(name = "group_policy", nullable = false)
    val groupPolicy: String,
    @Column(name = "group_id", nullable = false)
    val groupId: String,
    @Column(name = "file_upload_request_id", nullable = false)
    val fileUploadRequestId: String,
) {
    companion object {
        fun empty(): CpiMetadataEntity = CpiMetadataEntity(
            "", "", "", "", "", "", "", "", "", "")
    }

    @OneToMany(fetch = FetchType.EAGER, mappedBy="cpi")
    val cpks: Set<CpkMetadataEntity> = emptySet()

    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null
}

/** The composite primary key for a CpiEntity. */
@Embeddable
data class CpiMetadataEntityKey(
    private val name: String,
    private val version: String,
    private val signerSummaryHash: String,
): Serializable
