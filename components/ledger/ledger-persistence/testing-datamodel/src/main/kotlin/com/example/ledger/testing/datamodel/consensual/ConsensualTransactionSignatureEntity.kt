package com.example.ledger.testing.datamodel.consensual

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
@Table(name = "consensual_transaction_signature")
@IdClass(ConsensualTransactionSignatureEntityId::class)
data class ConsensualTransactionSignatureEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    val transaction: ConsensualTransactionEntity,

    @Id
    @Column(name = "signature_idx", nullable = false)
    val index: Int,

    @Column(name = "signature", nullable = false)
    val signature: ByteArray,

    @Column(name = "pub_key_hash", nullable = false)
    val publicKeyHash: String,

    @Column(name = "created", nullable = false)
    val created: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConsensualTransactionSignatureEntity

        if (transaction != other.transaction) return false
        if (index != other.index) return false
        if (!signature.contentEquals(other.signature)) return false
        if (publicKeyHash != other.publicKeyHash) return false
        if (created != other.created) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + index
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + publicKeyHash.hashCode()
        result = 31 * result + created.hashCode()
        return result
    }
}

@Embeddable
data class ConsensualTransactionSignatureEntityId(
    val transaction: ConsensualTransactionEntity,
    val index: Int
) : Serializable