package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import java.util.stream.Stream
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.OneToMany
import javax.persistence.Table

// This is not yet used
// private const val CPI_REVERSION_GENERATOR = "cpi_reversion_generator"

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
 * @property reversion reversion number
 */
@Entity
@Table(name = "cpi", schema = DbSchema.CONFIG)
@IdClass(CpiMetadataEntityKey::class)
@Suppress("LongParameterList")
data class CpiMetadataEntity (
    @Id
    @Column(name = "name", nullable = false)
    val name: String,
    @Id
    @Column(name = "version", nullable = false)
    val version: String,
    @Id
    @Column(name = "signer_summary_hash", nullable = false)
    val signerSummaryHash: String,
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
    /* This is not yet used
    import net.corda.db.schema.DbSchema.CPI_REVISION_SEQUENCE
    import net.corda.db.schema.DbSchema.CPI_REVISION_SEQUENCE_ALLOC_SIZE
    import javax.persistence.GeneratedValue
    import javax.persistence.GenerationType.SEQUENCE
    import javax.persistence.SequenceGenerator

    @SequenceGenerator(
        name = CPI_REVERSION_GENERATOR,
        sequenceName = CPI_REVISION_SEQUENCE,
        allocationSize = CPI_REVISION_SEQUENCE_ALLOC_SIZE
    )
    @GeneratedValue(strategy = SEQUENCE, generator = CPI_REVERSION_GENERATOR)
    */
    @Column(name = "entity_version", nullable = false)
    val reversion: Int,
) {
    companion object {
        fun empty(): CpiMetadataEntity = CpiMetadataEntity(
            "", "", "", "", "",
            "", "", "", -1)
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

// Although the following query is currently simple (so maybe it doesn't make sense to be here), however,
// in subsequent work where we add timestamps this will also take timestamp and maybe then it is worth
// testing the query, which should happen in this module.
fun EntityManager.findAllCpiMetadata(): Stream<CpiMetadataEntity> =
    createQuery("SELECT * from cpi", CpiMetadataEntity::class.java).resultStream