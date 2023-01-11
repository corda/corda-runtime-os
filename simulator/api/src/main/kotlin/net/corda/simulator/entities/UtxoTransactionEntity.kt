package net.corda.simulator.entities

import java.io.Serializable
import java.time.Instant
import java.util.Objects
import javax.persistence.*

@NamedQuery(
    name = "UtxoTransactionEntity.findByTransactionId",
    query = "from UtxoTransactionEntity t left join fetch t.signatures where t.id= :transactionId"
)
@Entity
@Table(name = "utxo_transaction")
class UtxoTransactionEntity(
    @Id
    @Column(name="id")
    val id: String,

    @Column(name="state_data")
    val stateData: ByteArray,

    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val signatures: MutableSet<UtxoTransactionSignatureEntity> = mutableSetOf(),

){
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionEntity

        return Objects.equals(id, other.id)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(id)
    }

    companion object {
        val UTXO_STATES_PERSISTENCE_CLASSES =
            listOf(
                UtxoTransactionEntity::class.java.name,
                UtxoTransactionSignatureEntity::class.java.name,
                UtxoTransactionEntityId::class.java.name,
                UtxoTransactionOutputEntity::class.java.name
            )
    }
}

@Entity
@Table(name = "utxo_transaction_signature")
@NamedQuery(
    name = "UtxoTransactionSignatureEntity.findByTransactionId",
    query = "from UtxoTransactionSignatureEntity where transaction.id = :transactionId"
)
@IdClass(UtxoTransactionEntityId::class)
class UtxoTransactionSignatureEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    val transaction: UtxoTransactionEntity,

    @Id
    @Column(name = "signature_idx", nullable = false)
    val index: Int,

    @Column(name = "key", nullable = false)
    val signatureWithKey: ByteArray,

    @Column(name = "timestamp", nullable = false)
    val timestamp: Instant
){
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionSignatureEntity

        return Objects.equals(transaction, other.transaction)
                && Objects.equals(index, other.index)
    }

    override fun hashCode(): Int {
        return Objects.hash(transaction, index)
    }
}

@Entity
@Table(name = "utxo_transaction_output")
@IdClass(UtxoTransactionOutputEntityId::class)
@NamedQuery(
    name = "UtxoTransactionOutputEntity.findUnconsumedStatesByType",
    query = "from UtxoTransactionOutputEntity where consumed = false and type = :type"
)
class UtxoTransactionOutputEntity(
    @Id
//    @ManyToOne
//    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    @Column(name = "id", nullable = false, updatable = false)
    val transactionId: String,

    @Column(name = "type", nullable = true)
    val type: String?,

    @Id
    @Column(name = "index", nullable = false)
    val index: Int,

    @Column(name = "consumed", nullable = false)
    var isConsumed: Boolean,

): Serializable {
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionOutputEntity

        return Objects.equals(transactionId, other.transactionId)
                && Objects.equals(index, other.index)
    }

    override fun hashCode(): Int {
        return Objects.hash(transactionId, index)
    }
}

@Embeddable
data class UtxoTransactionEntityId(
    val transaction: UtxoTransactionEntity,
    val index: Int
) : Serializable{
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionEntityId

        return Objects.equals(transaction, other.transaction)
                && Objects.equals(index, other.index)
    }

    override fun hashCode(): Int {
        return Objects.hash(transaction, index)
    }
}

@Embeddable
data class UtxoTransactionOutputEntityId(
    val transactionId: String,
    val index: Int
) : Serializable{
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionOutputEntityId

        return Objects.equals(transactionId, other.transactionId)
                && Objects.equals(index, other.index)
    }

    override fun hashCode(): Int {
        return Objects.hash(transactionId, index)
    }
}