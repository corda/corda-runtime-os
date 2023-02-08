package net.corda.libs.cpi.datamodel.entities

import net.corda.db.schema.DbSchema
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
class CpiMetadataEntity(
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
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true
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

    /**
     * We'll override to only use the primary key as the default equals causes issues when converting a stream to a list
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CpiMetadataEntity) return false

        if (name != other.name) return false
        if (version != other.version) return false
        if (signerSummaryHash != other.signerSummaryHash) return false

        return true
    }

    /**
     * We'll override to only use the primary key as the default equals causes issues when converting a stream to a list
     */
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + signerSummaryHash.hashCode()
        return result
    }

    fun copy(
        name: String = this.name,
        version: String = this.version,
        signerSummaryHash: String = this.signerSummaryHash,
        fileName: String = this.fileName,
        fileChecksum: String = this.fileChecksum,
        groupPolicy: String = this.groupPolicy,
        groupId: String = this.groupId,
        fileUploadRequestId: String = this.fileUploadRequestId,
        cpks: Set<CpiCpkEntity> = this.cpks,
        insertTimestamp: Instant? = this.insertTimestamp,
        isDeleted: Boolean = this.isDeleted,
        entityVersion: Int = this.entityVersion
    ) =
        CpiMetadataEntity(
            name,
            version,
            signerSummaryHash,
            fileName,
            fileChecksum,
            groupPolicy,
            groupId,
            fileUploadRequestId,
            cpks,
            insertTimestamp,
            isDeleted,
            entityVersion
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
                "INNER JOIN FETCH cpk_.metadata cpk_meta_ " +
                "ORDER BY cpi_.name, cpi_.version, cpi_.signerSummaryHash",
        CpiMetadataEntity::class.java
    ).resultStream
}
