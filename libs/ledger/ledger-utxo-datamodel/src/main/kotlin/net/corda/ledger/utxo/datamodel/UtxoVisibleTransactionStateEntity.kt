package net.corda.ledger.utxo.datamodel

import org.hibernate.annotations.Type
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

@Entity
@Table(name = "utxo_visible_transaction_state")
@IdClass(UtxoVisibleTransactionStateEntityId::class)
class UtxoVisibleTransactionStateEntity(
    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    var transactionId: String,

    @Id
    @Column(name = "group_idx", nullable = false)
    var groupIndex: Int,

    @Id
    @Column(name = "leaf_idx", nullable = false)
    var leafIndex: Int,

    @Column(name = "created", nullable = false)
    var created: Instant,

    @Column(name = "consumed", nullable = true)
    var consumed: Instant?

    // custom_representation is excluded, because there's no easy DB-agnostic way to handle JSON
    // fields in Hibernate (pre-v6).
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoVisibleTransactionStateEntity

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
        private const val serialVersionUID: Long = -3172841856710348528L
    }
}

@Embeddable
class UtxoVisibleTransactionStateEntityId(
    var transactionId: String,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoVisibleTransactionStateEntityId

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
        private const val serialVersionUID: Long = -3837822097209906298L
    }
}
