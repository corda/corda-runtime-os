package net.corda.libs.cpi.datamodel.entities.internal

import java.io.Serializable
import java.time.Instant
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.OneToOne
import javax.persistence.PreUpdate
import javax.persistence.Table
import javax.persistence.Version


/**
 * Cpi/cpk mapping table.
 */
@Entity
@Table(name = "cpi_cpk")
internal class CpiCpkEntity(
    @EmbeddedId
    var id: CpiCpkKey,

    @Column(name = "cpk_file_name", nullable = false)
    var cpkFileName: String,

    // note - orphanRemoval = false because a CPK could be associated with a different CPI.
    @OneToOne(cascade = [CascadeType.MERGE, CascadeType.PERSIST])
    @JoinColumn(
        name = "cpk_file_checksum",
        referencedColumnName = "file_checksum",
        insertable = false,
        updatable = false,
        nullable = false
    )
    var metadata: CpkMetadataEntity,

    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0
) {
    // Initial population of this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = true)
    var insertTimestamp: Instant? = null

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    @PreUpdate
    fun onUpdate() {
        insertTimestamp = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpiCpkEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Embeddable
internal class CpiCpkKey(
    @Column(name = "cpi_name", nullable = false)
    var cpiName: String,

    @Column(name = "cpi_version", nullable = false)
    var cpiVersion: String,

    @Column(name = "cpi_signer_summary_hash", nullable = false)
    var cpiSignerSummaryHash: String,

    @Column(name = "cpk_file_checksum", nullable = false)
    var cpkFileChecksum: String,
): Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpiCpkKey

        if (cpiName != other.cpiName) return false
        if (cpiVersion != other.cpiVersion) return false
        if (cpiSignerSummaryHash != other.cpiSignerSummaryHash) return false
        if (cpkFileChecksum != other.cpkFileChecksum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cpiName.hashCode()
        result = 31 * result + cpiVersion.hashCode()
        result = 31 * result + cpiSignerSummaryHash.hashCode()
        result = 31 * result + cpkFileChecksum.hashCode()
        return result
    }
}
