package com.example.ledger.testing.datamodel.consensual

import net.corda.v5.base.annotations.CordaSerializable
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

@CordaSerializable
@Entity
@Table(name = "consensual_transaction")
data class ConsensualTransactionEntity(
    @get:Id
    @get:Column(name = "id", nullable = false, updatable = false)
    var id: String,

    @get:Column(name = "privacy_salt", nullable = false)
    var privacySalt: ByteArray,

    @get:Column(name = "account_id", nullable = false)
    var accountId: String,

    @get:Column(name = "created", nullable = false)
    var created: Instant,
) {
    @get:OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    var components: MutableList<ConsensualTransactionComponentEntity> = mutableListOf()

    @get:OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    var statuses: MutableList<ConsensualTransactionStatusEntity> = mutableListOf()

    @get:OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    var signatures: MutableList<ConsensualTransactionSignatureEntity> = mutableListOf()

    @get:ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @get:JoinTable(
        name = "consensual_transaction_cpk",
        joinColumns = [JoinColumn(name = "transaction_id")],
        inverseJoinColumns = [JoinColumn(name = "file_checksum")]
    )
    var cpks: MutableSet<ConsensualCpkEntity> = mutableSetOf()

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
