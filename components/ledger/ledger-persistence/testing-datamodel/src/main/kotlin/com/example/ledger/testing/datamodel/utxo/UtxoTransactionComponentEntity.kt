package com.example.ledger.testing.datamodel.utxo

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
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
@Entity
@Table(name = "utxo_transaction_component")
@IdClass(UtxoTransactionComponentEntityId::class)
data class UtxoTransactionComponentEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    val transaction: UtxoTransactionEntity,

    @Id
    @Column(name = "group_idx", nullable = false)
    val groupIndex: Int,

    @Id
    @Column(name = "leaf_idx", nullable = false)
    val leafIndex: Int,

    @Column(name = "data", nullable = false)
    val data: ByteArray,

    @Column(name = "hash", nullable = false)
    val hash: String,

    @Column(name = "created", nullable = false)
    val created: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionComponentEntity

        if (transaction != other.transaction) return false
        if (groupIndex != other.groupIndex) return false
        if (leafIndex != other.leafIndex) return false
        if (!data.contentEquals(other.data)) return false
        if (hash != other.hash) return false
        if (created != other.created) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + leafIndex
        result = 31 * result + data.contentHashCode()
        result = 31 * result + hash.hashCode()
        result = 31 * result + created.hashCode()
        return result
    }
}

@Embeddable
data class UtxoTransactionComponentEntityId(
    val transaction: UtxoTransactionEntity,
    val groupIndex: Int,
    val leafIndex: Int
) : Serializable