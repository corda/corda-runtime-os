package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.JoinColumns
import javax.persistence.OneToOne
import javax.persistence.PreUpdate
import javax.persistence.Table
import javax.persistence.Version


/**
 * Cpi/cpk mapping table.
 */
@Entity
@Table(name = "cpi_cpk", schema = DbSchema.CONFIG)
data class CpiCpkEntity(
    @EmbeddedId
    private val id: CpiCpkKey,
    @Column(name = "cpk_file_name", nullable = false)
    val cpkFileName: String,
    @Column(name = "cpk_file_checksum", nullable = false)
    val cpkFileChecksum: String,
    @OneToOne(cascade = [CascadeType.MERGE, CascadeType.PERSIST])
    @JoinColumns(
        JoinColumn(name = "cpk_name", referencedColumnName = "cpk_name", insertable = false, updatable = false),
        JoinColumn(name = "cpk_version", referencedColumnName = "cpk_version", insertable = false, updatable = false),
        JoinColumn(
            name = "cpk_signer_summary_hash",
            referencedColumnName = "cpk_signer_summary_hash",
            insertable = false,
            updatable = false
        ),
    )
    val metadata: CpkMetadataEntity,
) {
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0
    // Initial population of this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = true)
    var insertTimestamp: Instant? = null
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    @PreUpdate
    fun onUpdate() {
        insertTimestamp = Instant.now()
    }
}

@Embeddable
data class CpiCpkKey(
    @Column(name = "cpi_name")
    private val cpiName: String,
    @Column(name = "cpi_version")
    private val cpiVersion: String,
    @Column(name = "cpi_signer_summary_hash")
    private val cpiSignerSummaryHash: String,
    @Column(name = "cpk_name")
    private val cpkName: String,
    @Column(name = "cpk_version")
    private val cpkVersion: String,
    @Column(name = "cpk_signer_summary_hash")
    private val cpkSignerSummaryHash: String,
): Serializable