package com.example.ledger.testing.datamodel.utxo

import net.corda.v5.base.annotations.CordaSerializable
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.NamedQuery
import javax.persistence.Table

@CordaSerializable
@NamedQuery(
    name = "UtxoTransactionSourceEntity.findByTransactionId",
    query = "from UtxoTransactionSourceEntity where transaction.id = :transactionId"
)
@Entity
@Table(name = "utxo_transaction_sources")
@IdClass(UtxoTransactionSourceEntityId::class)
data class UtxoTransactionSourceEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    val transaction: UtxoTransactionEntity,

    @Id
    @Column(name = "group_idx", nullable = false)
    val groupIndex: Int,

    @Id
    @Column(name = "leaf_idx", nullable = false)
    val leafIndex: Int,

    @Column(name = "ref_transaction_id", nullable = false)
    val refTransactionId: String,

    @Column(name = "ref_leaf_idx", nullable = false)
    val refLeafIndex: Int,

    @Column(name = "is_ref_input", nullable = false)
    val isRefInput: Boolean,

    @Column(name = "created", nullable = false)
    val created: Instant
)

@Embeddable
data class UtxoTransactionSourceEntityId(
    val transaction: UtxoTransactionEntity,
    val groupIndex: Int,
    val leafIndex: Int
) : Serializable