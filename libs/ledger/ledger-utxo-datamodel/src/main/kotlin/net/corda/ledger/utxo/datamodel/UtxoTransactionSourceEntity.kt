package net.corda.ledger.utxo.datamodel

import net.corda.v5.base.annotations.CordaSerializable
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@CordaSerializable
@Entity
@Table(name = "utxo_transaction_sources")
@IdClass(UtxoTransactionSourceEntityId::class)
class UtxoTransactionSourceEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    var transaction: UtxoTransactionEntity,

    @Id
    @Column(name = "group_idx", nullable = false)
    var groupIndex: Int,

    @Id
    @Column(name = "leaf_idx", nullable = false)
    var leafIndex: Int,

    @Column(name = "ref_transaction_id", nullable = false)
    var refTransactionId: String,

    @Column(name = "ref_leaf_idx", nullable = false)
    var refLeafIndex: Int,

    @Column(name = "is_ref_input", nullable = false)
    var isRefInput: Boolean,

    @Column(name = "created", nullable = false)
    var created: Instant
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionSourceEntity

        if (transaction != other.transaction) return false
        if (groupIndex != other.groupIndex) return false
        if (leafIndex != other.leafIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + leafIndex
        return result
    }

    companion object {
        private const val serialVersionUID: Long = -5220443889767762884L
    }
}

@Embeddable
class UtxoTransactionSourceEntityId(
    var transaction: UtxoTransactionEntity,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionSourceEntityId

        if (transaction != other.transaction) return false
        if (groupIndex != other.groupIndex) return false
        if (leafIndex != other.leafIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + leafIndex
        return result
    }

    companion object {
        private const val serialVersionUID: Long = -675271045815033627L
    }
}
