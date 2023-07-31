package net.corda.ledger.utxo.datamodel

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

@Entity
@Table(name = "utxo_transaction_component")
@IdClass(UtxoTransactionComponentEntityId::class)
class UtxoTransactionComponentEntity(
    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    var transactionId: String,

    @Id
    @Column(name = "group_idx", nullable = false)
    var groupIndex: Int,

    @Id
    @Column(name = "leaf_idx", nullable = false)
    var leafIndex: Int,

    @Column(name = "data", nullable = false)
    var data: ByteArray,

    @Column(name = "hash", nullable = false)
    var hash: String,

    @Column(name = "created", nullable = false)
    var created: Instant
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionComponentEntity

        if (transactionId != other.transactionId) return false
        if (groupIndex != other.groupIndex) return false
        if (leafIndex != other.leafIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + leafIndex

        return result
    }

    companion object {
        private const val serialVersionUID: Long = 6041632694894522684L
    }
}

@Embeddable
class UtxoTransactionComponentEntityId(
    var transactionId: String,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionComponentEntityId

        if (transactionId != other.transactionId) return false
        if (groupIndex != other.groupIndex) return false
        if (leafIndex != other.leafIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + leafIndex
        return result
    }

    companion object {
        private const val serialVersionUID: Long = 1175691165696970977L
    }
}
