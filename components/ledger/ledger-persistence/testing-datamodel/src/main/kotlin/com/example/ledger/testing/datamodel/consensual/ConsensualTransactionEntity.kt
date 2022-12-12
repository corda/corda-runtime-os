package com.example.ledger.testing.datamodel.consensual

import java.time.Instant
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.OneToMany
import javax.persistence.Table
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
@Entity
@Table(name = "consensual_transaction")
data class ConsensualTransactionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: String,

    @Column(name = "privacy_salt", nullable = false)
    val privacySalt: ByteArray,

    @Column(name = "account_id", nullable = false)
    val accountId: String,

    @Column(name = "created", nullable = false)
    val created: Instant,
) {
    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    val components: MutableList<ConsensualTransactionComponentEntity> = mutableListOf()

    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    val statuses: MutableList<ConsensualTransactionStatusEntity> = mutableListOf()

    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    val signatures: MutableList<ConsensualTransactionSignatureEntity> = mutableListOf()

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(name = "consensual_transaction_cpk",
        joinColumns = [JoinColumn(name = "transaction_id")],
        inverseJoinColumns = [JoinColumn(name = "file_checksum")]
    )
    val cpks: MutableSet<ConsensualCpkEntity> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConsensualTransactionEntity

        if (id != other.id) return false
        if (!privacySalt.contentEquals(other.privacySalt)) return false
        if (accountId != other.accountId) return false
        if (created != other.created) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + privacySalt.contentHashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + created.hashCode()
        return result
    }
}
