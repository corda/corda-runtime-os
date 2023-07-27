package net.corda.simulator.entities

import java.io.Serializable
import java.time.Instant
import java.util.Objects
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "consensual_transaction")
class ConsensualTransactionEntity (
    @get:Id
    @get:Column(name="id")
    var id: String,

    @get:Column(name="state_data")
    var stateData: ByteArray,

    @get:Column(name="timestamp")
    var timestamp: Instant,

    @get:OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var signatures: MutableSet<ConsensualTransactionSignatureEntity> = mutableSetOf()
){
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConsensualTransactionEntity

        return Objects.equals(id, other.id)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(id)
    }

    companion object {
        val CONSENSUAL_STATES_PERSISTENCE_CLASSES =
            listOf(
                ConsensualTransactionEntity::class.java.name,
                ConsensualTransactionSignatureEntity::class.java.name,
                ConsensualTransactionSignatureEntityId::class.java.name
            )
    }
}

@Entity
@Table(name = "consensual_transaction_signature")
@IdClass(ConsensualTransactionSignatureEntityId::class)
class ConsensualTransactionSignatureEntity(

    @get:Id
    @get:ManyToOne
    @get:JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    var transaction: ConsensualTransactionEntity,

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

        other as ConsensualTransactionSignatureEntity

        return Objects.equals(transaction, other.transaction)
                && Objects.equals(index, other.index)
    }

    override fun hashCode(): Int {
        return Objects.hash(transaction, index)
    }
}

@Embeddable
class ConsensualTransactionSignatureEntityId(
    var transaction: ConsensualTransactionEntity,
    var index: Int
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConsensualTransactionSignatureEntityId

        return Objects.equals(transaction, other.transaction)
                && Objects.equals(index, other.index)
    }

    override fun hashCode(): Int {
        return Objects.hash(transaction, index)
    }
}
