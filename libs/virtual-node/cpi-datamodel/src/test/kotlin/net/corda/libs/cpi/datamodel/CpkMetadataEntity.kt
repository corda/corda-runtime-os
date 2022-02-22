package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.JoinColumns
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * Cpk Entity without binary data
 *
 * @property cpi The [CpiMetadataEntity] object this CPK relates to
 * @property cpkFileChecksum checksum of the CPK binary - this is the PK for the [CpkDataEntity] entity
 * @property cpkFileName for the CPK as defined in the CPI.
 *                      NOTE: it is possible to have CPKs know by different filenames in case they
 *                      where packaged up in different CPIs.
 *                      This property should only be used for information, not as a key.
 * @property insertTimestamp when the CPK Entity was inserted
 */
@Entity
@Table(name = "cpi_cpk", schema = DbSchema.CONFIG)
@IdClass(CpkMetadataEntityKey::class)
data class CpkMetadataEntity (
    @Id
    @ManyToOne
    @JoinColumns(
        JoinColumn(name="cpi_name", referencedColumnName="name", insertable=false, updatable=false),
        JoinColumn(name="cpi_version", referencedColumnName="version", insertable=false, updatable=false),
        JoinColumn(name="cpi_signer_summary_hash", referencedColumnName="signer_summary_hash", insertable=false, updatable=false)
    )
    val cpi: CpiMetadataEntity = CpiMetadataEntity.empty(),

    // NOTE: not mapping to CPK data as it would be a potentially bad idea to fetch all the CPK data.
    //  generally one-by-one loading is going to be better for the files.
    @Id
    @Column(name = "cpk_file_checksum", nullable = false)
    val cpkFileChecksum: String,
    @Column(name = "cpk_file_name", nullable = false)
    val cpkFileName: String,
) {
    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null
}

/** The composite primary key for a CpkEntity. */
@Embeddable
data class CpkMetadataEntityKey(val cpi: CpiMetadataEntity, val cpkFileChecksum: String): Serializable