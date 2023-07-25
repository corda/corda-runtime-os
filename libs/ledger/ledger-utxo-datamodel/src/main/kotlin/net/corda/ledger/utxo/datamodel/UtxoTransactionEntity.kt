package net.corda.ledger.utxo.datamodel

import net.corda.v5.base.annotations.CordaSerializable
import java.io.Serializable
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
@Table(name = "utxo_transaction")
class UtxoTransactionEntity(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    var id: String,

    @Column(name = "privacy_salt", nullable = false)
    var privacySalt: ByteArray,

    @Column(name = "account_id", nullable = false)
    var accountId: String,

    @Column(name = "created", nullable = false)
    var created: Instant,
) : Serializable {
    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    var components: MutableList<UtxoTransactionComponentEntity> = mutableListOf()

    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    var statuses: MutableList<UtxoTransactionStatusEntity> = mutableListOf()

    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    var signatures: MutableList<UtxoTransactionSignatureEntity> = mutableListOf()

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(name = "utxo_transaction_cpk",
        joinColumns = [JoinColumn(name = "transaction_id")],
        inverseJoinColumns = [JoinColumn(name = "file_checksum")]
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        private const val serialVersionUID: Long = -8412188174627907588L
    }
}
