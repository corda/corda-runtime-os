package com.example.ledger.testing.datamodel.utxo

import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.Table
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
@Entity
@Table(name = "utxo_transaction_metadata")
data class UtxoTransactionMetadataEntity(
    @get:Id
    @get:Column(name = "hash", nullable = false, updatable = false)
    var hash: String,

    @get:Column(name = "canonical_data", nullable = false)
    var canonicalData: ByteArray,

    @get:Column(name = "group_parameters_hash", nullable = false)
    var groupParametersHash: String,

    @get:Column(name = "cpi_file_checksum", nullable = false)
    var cpiFileChecksum: String

) {
    @get:OneToMany(mappedBy = "metadata", cascade = [CascadeType.ALL], orphanRemoval = true)
    var transactions: MutableList<UtxoTransactionEntity> = mutableListOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionMetadataEntity

        if (hash != other.hash) return false
        if (!canonicalData.contentEquals(other.canonicalData)) return false
        if (groupParametersHash != other.groupParametersHash) return false
        if (cpiFileChecksum != other.cpiFileChecksum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.hashCode()
        result = 31 * result + canonicalData.contentHashCode()
        result = 31 * result + groupParametersHash.hashCode()
        result = 31 * result + cpiFileChecksum.hashCode()
        return result
    }
}
