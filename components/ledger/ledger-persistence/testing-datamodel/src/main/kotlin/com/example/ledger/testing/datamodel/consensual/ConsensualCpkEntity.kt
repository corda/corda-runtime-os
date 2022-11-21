package com.example.ledger.testing.datamodel.consensual

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
@Entity
@Table(name = "consensual_cpk")
data class ConsensualCpkEntity(
    @Id
    @Column(name = "file_checksum", nullable = false)
    val fileChecksum: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "signer_summary_hash", nullable = false)
    val signerSummaryHash: String,

    @Column(name = "version", nullable = false)
    val version: String,

    @Column(name = "data", nullable = false)
    @Lob
    val data: ByteArray,

    @Column(name = "created", nullable = false)
    val created: Instant

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConsensualCpkEntity

        if (fileChecksum != other.fileChecksum) return false
        if (name != other.name) return false
        if (signerSummaryHash != other.signerSummaryHash) return false
        if (version != other.version) return false
        if (!data.contentEquals(other.data)) return false
        if (created != other.created) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileChecksum.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + signerSummaryHash.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + created.hashCode()
        return result
    }
}