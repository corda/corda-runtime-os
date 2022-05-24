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
import javax.persistence.Version

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
 * @property entityVersion Entity version number
 * @property isDeleted Flag used for soft db deletes
 */
@Entity
@Table(name = "cpi", schema = DbSchema.CONFIG)
@IdClass(CpiMetadataEntityKey::class)
@Suppress("LongParameterList")
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
    @Column(name = "file_name", nullable = false)
    var fileName: String,
    @Column(name = "file_checksum", nullable = false)
    var fileChecksum: String,
    @Column(name = "group_policy", nullable = false)
    var groupPolicy: String,
    @Column(name = "group_id", nullable = false)
    var groupId: String,
    @Column(name = "file_upload_request_id", nullable = false)
    var fileUploadRequestId: String,
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean
) {
    companion object {
        fun empty(): CpiMetadataEntity = CpiMetadataEntity(
            "", "", "", "", "",
            "", "", "",false
        )
    }

    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0

    @OneToMany(fetch = FetchType.EAGER, mappedBy="cpi")
    val cpks: Set<CpkMetadataEntity> = emptySet()

    // Initial population of this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = true)
    var insertTimestamp: Instant? = null
}

/** The composite primary key for a CpiEntity. */
@Embeddable
data class CpiMetadataEntityKey(
    private val name: String,
    private val version: String,
    private val signerSummaryHash: String,
): Serializable

// TODO The below needs fixing. It currently seems to be producing a select query over Cpi metadata
//  and one select query per Cpk metadata, as per https://r3-cev.atlassian.net/browse/CORE-4864
fun EntityManager.findAllCpiMetadata(): Stream<CpiMetadataEntity> {
    return createQuery(
        "FROM ${CpiMetadataEntity::class.simpleName} cpi_ " +
                "left join fetch cpi_.cpks cpks_ " +
                "left join fetch cpks_.cpkLibraries " +
                "left join fetch cpks_.cpkDependencies ",
        CpiMetadataEntity::class.java
    ).resultStream
}