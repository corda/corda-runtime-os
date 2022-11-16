package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import net.corda.v5.base.util.uncheckedCast
import java.io.Serializable
import java.time.Instant
import java.util.stream.Stream
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.JoinColumns
import javax.persistence.OneToMany
import javax.persistence.PreUpdate
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
    @OneToMany(
        fetch = FetchType.EAGER,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE]
    )
    @JoinColumns(
        JoinColumn(name = "cpi_name", referencedColumnName = "name", insertable = false, updatable = false),
        JoinColumn(name = "cpi_version", referencedColumnName = "version", insertable = false, updatable = false),
        JoinColumn(
            name = "cpi_signer_summary_hash",
            referencedColumnName = "signer_summary_hash",
            insertable = false,
            updatable = false
        ),
    )
    val cpks: Set<CpiCpkEntity>,
    // Initial population of this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = true)
    var insertTimestamp: Instant? = null,
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0,
) {
    companion object {
        // Create a [CpiMetadataEntity] with CPKs as filename/metadata pairs
        fun create(
            name: String,
            version: String,
            signerSummaryHash: String,
            fileName: String,
            fileChecksum: String,
            groupPolicy: String,
            groupId: String,
            fileUploadRequestId: String,
            cpks: Set<CpiCpkEntity>
        ): CpiMetadataEntity {
            return CpiMetadataEntity(
                name,
                version,
                signerSummaryHash,
                fileName,
                fileChecksum,
                groupPolicy,
                groupId,
                fileUploadRequestId,
                cpks
            )
        }
    }

    @PreUpdate
    fun onUpdate() {
        insertTimestamp = Instant.now()
    }

    // return a clone of this object with the updated properties
    fun update(
        fileUploadRequestId: String,
        fileName: String,
        fileChecksum: String,
        cpks: Set<CpiCpkEntity>
    ) =
        this.copy(
            fileUploadRequestId = fileUploadRequestId,
            fileName = fileName,
            fileChecksum = fileChecksum,
            cpks = cpks,
        )
}

/** The composite primary key for a CpiEntity. */
@Embeddable
data class CpiMetadataEntityKey(
    private val name: String,
    private val version: String,
    private val signerSummaryHash: String,
) : Serializable

fun EntityManager.findAllCpiMetadata(): Stream<CpiMetadataEntity> {
    // Joining the other tables to ensure all data is fetched eagerly
    return createQuery(
        "FROM ${CpiMetadataEntity::class.simpleName} cpi_ " +
                "INNER JOIN FETCH cpi_.cpks cpk_ " +
                "INNER JOIN FETCH cpk_.metadata cpk_meta_",
        CpiMetadataEntity::class.java
    ).resultStream
}

@Suppress("unused")
fun EntityManager.findAllCpiMetadata2(): Stream<CpiMetadataEntity> {
    // Joining the other tables to ensure all data is fetched eagerly
    return uncheckedCast<Any, Stream<CpiMetadataEntity>>(createNativeQuery(
        "SELECT * FROM CONFIG.cpi cpi_ ",// +
//                "INNER JOIN CONFIG.cpi_cpk cpk_ on cpi_.name=cpk_.cpi_name and cpi_signer_summary_hash=cpk_.cpi_signer_summary_hash and cpi_.version=cpk_.cpi_version " +
//                "INNER JOIN CONFIG.cpk_metadata cpkmetadata_ on cpk_.cpk_name=cpkmetadata_.cpk_name \n" +
//                "            and cpk_.cpk_signer_summary_hash=cpkmetadata_.cpk_signer_summary_hash \n" +
//                "            and cpk_.cpk_version=cpkmetadata_.cpk_version",
        CpiMetadataEntity::class.java
    ).resultStream)
}
