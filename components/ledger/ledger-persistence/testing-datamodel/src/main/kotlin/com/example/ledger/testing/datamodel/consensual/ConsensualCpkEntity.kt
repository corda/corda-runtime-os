package com.example.ledger.testing.datamodel.consensual

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table

@CordaSerializable
@Entity
@Table(name = "consensual_cpk")
data class ConsensualCpkEntity(
    @get:Id
    @get:Column(name = "file_checksum", nullable = false)
    var fileChecksum: String,

    @get:Column(name = "name", nullable = false)
    var name: String,

    @get:Column(name = "signer_summary_hash", nullable = false)
    var signerSummaryHash: String,

    @get:Column(name = "version", nullable = false)
    var version: String,

    @get:Column(name = "data", nullable = false)
    @get:Lob
    var data: ByteArray,

    @get:Column(name = "created", nullable = false)
    var created: Instant

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
