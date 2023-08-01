package net.corda.simulator.entities

import java.io.Serializable
import java.time.Instant
import java.util.Objects
import javax.persistence.NamedQuery
import javax.persistence.Entity
import javax.persistence.Table
import javax.persistence.Id
import javax.persistence.Column
import javax.persistence.OneToMany
import javax.persistence.ManyToOne
import javax.persistence.FetchType
import javax.persistence.CascadeType
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.Embeddable

/***
 * This entity stores Utxo Transaction data
 */
@Suppress("LongParameterList")
@Entity
@Table(name = "utxo_transaction")
class UtxoTransactionEntity(
    @get:Id
    @get:Column(name="tx_id")
    var id: String,

    @get:Column(name="command_data")
    var commandData: ByteArray,

    @get:Column(name="input_data")
    var inputData: ByteArray,

    @get:Column(name="reference_state_data")
    var referenceStateDate: ByteArray,

    @get:Column(name="signatories_data")
    var signatoriesData: ByteArray,

    @get:Column(name="time_window_data")
    var timeWindowData: ByteArray,

    @get:Column(name="output_data")
    var outputData: ByteArray,

    @get:Column(name="attachment_data")
    var attachmentData: ByteArray,

    @get:OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var signatures: MutableSet<UtxoTransactionSignatureEntity> = mutableSetOf(),

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

/***
 * Entity to store signature data associated with a signed utxo transaction
 */
@Entity
@Table(name = "utxo_transaction_signature")
@IdClass(UtxoTransactionEntityId::class)
class UtxoTransactionSignatureEntity(
    @get:Id
    @get:ManyToOne
    @get:JoinColumn(name = "tx_id", nullable = false, updatable = false)
    var transaction: UtxoTransactionEntity,

    @get:Id
    @get:Column(name = "signature_idx", nullable = false)
    var index: Int,

    @get:Column(name = "key", nullable = false)
    var signatureWithKey: ByteArray,

    @get:Column(name = "timestamp", nullable = false)
    var timestamp: Instant
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


/***
 * This entity stores outputs of a transaction and is used to manage/ fetch unconsumed states in simulator
 */
@Suppress("LongParameterList")
@Entity
@Table(name = "utxo_transaction_output")
@IdClass(UtxoTransactionOutputEntityId::class)
@NamedQuery(
    name = "UtxoTransactionOutputEntity.findUnconsumedStatesByType",
    query = "from UtxoTransactionOutputEntity where consumed = false and type = :type"
)
class UtxoTransactionOutputEntity(
    @get:Id
    @get:Column(name = "tx_id", nullable = false, updatable = false)
    var transactionId: String,

    @get:Column(name = "type", nullable = true)
    var type: String?,

    @get:Column(name="encumbrance_data")
    var encumbranceData: ByteArray,

    @get:Column(name="state_data")
    var stateData: ByteArray,

    @get:Id
    @get:Column(name = "index", nullable = false)
    var index: Int,

    @get:Column(name = "consumed", nullable = false)
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
    var transaction: UtxoTransactionEntity,
    var index: Int
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
    var transactionId: String,
    var index: Int
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