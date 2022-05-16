package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import javax.persistence.AttributeOverride
import javax.persistence.AttributeOverrides
import javax.persistence.CollectionTable
import javax.persistence.Column
import javax.persistence.ElementCollection
import javax.persistence.Embeddable
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.JoinColumns
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.OneToOne
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
data class CpkMetadataEntity(
    @Id
    @ManyToOne
    @JoinColumns(
        JoinColumn(name = "cpi_name", referencedColumnName = "name", insertable = false, updatable = false),
        JoinColumn(name = "cpi_version", referencedColumnName = "version", insertable = false, updatable = false),
        JoinColumn(name = "cpi_signer_summary_hash", referencedColumnName = "signer_summary_hash", insertable = false, updatable = false)
    )
    val cpi: CpiMetadataEntity = CpiMetadataEntity.empty(),

    // NOTE: not mapping to CPK data as it would be a potentially bad idea to fetch all the CPK data.
    //  generally one-by-one loading is going to be better for the files.
    @Id
    @Column(name = "cpk_file_checksum", nullable = false)
    val cpkFileChecksum: String,
    @Column(name = "cpk_file_name", nullable = false)
    val cpkFileName: String,
    // following 3 properties are CpkIdentifier. Maybe we need to bring that back
    @Column(name = "cpk_main_bundle_name", nullable = false)
    val mainBundleName: String,
    @Column(name = "cpk_main_bundle_version", nullable = false)
    val mainBundleVersion: String,
    @Column(name = "cpk_signer_summary_hash", nullable = false)
    val signerSummaryHash: String,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "cpkFormatVersion.major", column = Column(name = "cpk_manifest_major_version")),
            AttributeOverride(name = "cpkFormatVersion.minor", column = Column(name = "cpk_manifest_minor_version"))
        ]
    )
    val cpkManifest: CpkManifest,
    @Column(name = "cpk_main_bundle")
    val cpkMainBundle: String,
    @Column(name = "cpk_type", nullable = true)
    val cpkType: String?,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "cpk_library",
        schema = DbSchema.CONFIG,
        joinColumns = [
            JoinColumn(name = "cpi_name", referencedColumnName = "cpi_name", insertable = false, updatable = false),
            JoinColumn(name = "cpi_version", referencedColumnName = "cpi_version", insertable = false, updatable = false),
            JoinColumn(
                name = "cpi_signer_summary_hash", referencedColumnName = "cpi_signer_summary_hash",
                insertable = false, updatable = false
            ),
            JoinColumn(
                name = "cpk_file_checksum", referencedColumnName = "cpk_file_checksum",
                insertable = false, updatable = false
            )
        ]
    )
    @Column(name = "library_name")
    val cpkLibraries: List<String>
    // TODO cordappCertificates // bytes we have it elsewhere look it up
) {
    // If set to FetchType.EAGER below we then getting a stack overflow exception
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "cpkMetadataEntity")
    val cpkDependencies: Set<CpkDependencyEntity> = emptySet()
    @OneToOne(fetch = FetchType.EAGER, mappedBy = "cpkMetadataEntity")
    val cpkCordappManifest: CpkCordappManifestEntity? = null
    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null
}

/** The composite primary key for a [CpkMetadataEntity]. */
@Embeddable
data class CpkMetadataEntityKey(val cpi: CpiMetadataEntity, val cpkFileChecksum: String): Serializable

@Entity
@IdClass(CpkDependencyEntityKey::class)
@Table(name = "cpk_dependency", schema = DbSchema.CONFIG)
data class CpkDependencyEntity(
    @Id
    @ManyToOne
    @JoinColumns(
        JoinColumn(name = "cpi_name", referencedColumnName = "cpi_name", insertable = false, updatable = false),
        JoinColumn(name = "cpi_version", referencedColumnName = "cpi_version", insertable = false, updatable = false),
        JoinColumn(
            name = "cpi_signer_summary_hash", referencedColumnName = "cpi_signer_summary_hash", insertable = false, updatable = false),
        JoinColumn(
            name = "cpk_file_checksum", referencedColumnName = "cpk_file_checksum", insertable = false, updatable = false)
    )
    val cpkMetadataEntity: CpkMetadataEntity,

    @Id
    @Column(name = "main_bundle_name", nullable = false)
    val mainBundleName: String,
    @Id
    @Column(name = "main_bundle_version", nullable = false)
    val mainBundleVersion: String,
    @Id
    @Column(name = "signer_summary_hash", nullable = false)
    val signerSummaryHash: String
) : Serializable

@Embeddable
data class CpkDependencyEntityKey(
    val cpkMetadataEntity: CpkMetadataEntity,
    val mainBundleName: String,
    val mainBundleVersion: String,
    val signerSummaryHash: String
) : Serializable

@Embeddable
data class CpkManifest(
    @Embedded
    val cpkFormatVersion: CpkFormatVersion
)

@Embeddable
data class CpkFormatVersion(val major: Int, val minor: Int)

@Entity
@Table(name = "cpk_cordapp_manifest", schema = DbSchema.CONFIG)
data class CpkCordappManifestEntity(
    @Id
    @OneToOne
    @JoinColumns(
        JoinColumn(name = "cpi_name", referencedColumnName = "cpi_name", insertable = false, updatable = false),
        JoinColumn(name = "cpi_version", referencedColumnName = "cpi_version", insertable = false, updatable = false),
        JoinColumn(
            name = "cpi_signer_summary_hash", referencedColumnName = "cpi_signer_summary_hash",
            insertable = false, updatable = false
        ),
        JoinColumn(name = "cpk_file_checksum", referencedColumnName = "cpk_file_checksum", insertable = false, updatable = false)
    )
    val cpkMetadataEntity: CpkMetadataEntity,

    @Column(name = "bundle_symbolic_name", nullable = false)
    val bundleSymbolicName: String,
    @Column(name = "bundle_version", nullable = false)
    val bundleVersion: String,
    @Column(name = "min_platform_version", nullable = false)
    val minPlatformVersion: Int,
    @Column(name = "target_platform_version", nullable = false)
    val targetPlatformVersion: Int,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "shortName", column = Column(name = "contract_info_short_name", nullable = true)),
            AttributeOverride(name = "vendor", column = Column(name = "contract_info_vendor", nullable = true)),
            AttributeOverride(name = "versionId", column = Column(name = "contract_info_version_id", nullable = true)),
            AttributeOverride(name = "license", column = Column(name = "contract_info_license", nullable = true))
        ]
    )
    val contractInfo: ManifestCorDappInfo,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "shortName", column = Column(name = "work_flow_info_short_name", nullable = true)),
            AttributeOverride(name = "vendor", column = Column(name = "work_flow_info_vendor", nullable = true)),
            AttributeOverride(name = "versionId", column = Column(name = "work_flow_info_version_id", nullable = true)),
            AttributeOverride(name = "license", column = Column(name = "work_flow_info_license", nullable = true))
        ]
    )
    val workflowInfo: ManifestCorDappInfo,
    // TODO attributes
) : Serializable

@Embeddable
data class ManifestCorDappInfo(
    val shortName: String?,
    val vendor: String?,
    val versionId: Int?,
    val license: String?
)