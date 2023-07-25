package net.corda.ledger.utxo.datamodel

import java.io.Serializable
import java.math.BigDecimal
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
@Table(name = "utxo_transaction_output")
@IdClass(UtxoTransactionOutputEntityId::class)
class UtxoTransactionOutputEntity(
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

    @Column(name = "type", nullable = true)
    var type: String?,

    @Column(name = "token_type", nullable = true)
    var tokenType: String?,

    @Column(name = "token_issuer_hash", nullable = true)
    var tokenIssuerHash: String?,

    @Column(name = "token_notary_x500_name", nullable = true)
    var tokenNotaryX500Name: String?,

    @Column(name = "token_symbol", nullable = true)
    var tokenSymbol: String?,

    @Column(name = "token_tag", nullable = true)
    var tokenTag: String?,

    @Column(name = "token_owner_hash", nullable = true)
    var tokenOwnerHash: String?,

    @Column(name = "token_amount", nullable = true)
    var tokenAmount: BigDecimal?,

    @Column(name = "created", nullable = false)
    var created: Instant
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionOutputEntity

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
        private const val serialVersionUID: Long = 7461154388205723163L
    }
}

@Embeddable
data class UtxoTransactionOutputEntityId(
    val transaction: UtxoTransactionEntity,
    val groupIndex: Int,
    val leafIndex: Int
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 7461849704435934357L
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionOutputEntityId

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
}
