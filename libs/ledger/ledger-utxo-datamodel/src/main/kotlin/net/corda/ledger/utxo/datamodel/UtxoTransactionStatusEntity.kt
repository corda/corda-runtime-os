package net.corda.ledger.utxo.datamodel

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "utxo_transaction_status")
class UtxoTransactionStatusEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    var transaction: UtxoTransactionEntity,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "updated", nullable = false)
    var updated: Instant
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionStatusEntity

        if (transaction != other.transaction) return false

        return true
    }

    override fun hashCode(): Int {
        return transaction.hashCode()
    }

    companion object {
        private const val serialVersionUID: Long = -7216774475405678921L
    }
}
