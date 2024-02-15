package com.example.ledger.testing.datamodel.utxo

import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.NamedQuery
import javax.persistence.Table

@CordaSerializable
@Entity
@Table(name = "utxo_transaction_merkle_proof")
@NamedQuery(
    name = "UtxoMerkleProofEntity.findByTransactionId",
    query = "from UtxoMerkleProofEntity where transactionId = :transactionId"
)
data class UtxoMerkleProofEntity(
    @get:Id
    @get:Column(name = "merkle_proof_id", nullable = false, updatable = false)
    var merkleProofId: String,

    @get:Column(name = "transaction_id", nullable = false, updatable = false)
    var transactionId: String,

    @get:Column(name = "group_idx", nullable = false, updatable = false)
    var groupIndex: Int,

    @get:Column(name = "tree_size", nullable = false, updatable = false)
    var treeSize: Int,

    @get:Column(name = "hashes", nullable = false)
    var hashes: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoMerkleProofEntity

        if (merkleProofId != other.merkleProofId) return false
        if (transactionId != other.transactionId) return false
        if (groupIndex != other.groupIndex) return false
        if (treeSize != other.treeSize) return false
        if (hashes != other.hashes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = merkleProofId.hashCode()
        result = 31 * result + transactionId.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + treeSize
        result = 31 * result + hashes.hashCode()
        return result
    }
}
