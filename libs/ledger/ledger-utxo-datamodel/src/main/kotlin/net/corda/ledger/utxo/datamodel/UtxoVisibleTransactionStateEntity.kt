package net.corda.ledger.utxo.datamodel

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

@Entity
@Table(name = "utxo_visible_transaction_state")
@IdClass(UtxoVisibleTransactionStateEntityId::class)
class UtxoVisibleTransactionStateEntity(
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

    @Column(name = "custom_representation", nullable = false, columnDefinition = "jsonb")
    var customRepresentation: String,

    @Column(name = "created", nullable = false)
    var created: Instant,

    @Column(name = "consumed", nullable = true)
    var consumed: Instant?
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoVisibleTransactionStateEntity

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
        private const val serialVersionUID: Long = -3172841856710348528L
    }
}

@Embeddable
class UtxoVisibleTransactionStateEntityId(
    var transaction: UtxoTransactionEntity,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoVisibleTransactionStateEntityId

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
        private const val serialVersionUID: Long = -3837822097209906298L
    }
}
