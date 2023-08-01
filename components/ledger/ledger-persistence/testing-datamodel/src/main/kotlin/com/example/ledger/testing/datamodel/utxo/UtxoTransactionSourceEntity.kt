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
    @get:Id
    @get:ManyToOne
    @get:JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    var transaction: UtxoTransactionEntity,

    @get:Id
    @get:Column(name = "group_idx", nullable = false)
    var groupIndex: Int,

    @get:Id
    @get:Column(name = "leaf_idx", nullable = false)
    var leafIndex: Int,

    @get:Column(name = "ref_transaction_id", nullable = false)
    var refTransactionId: String,

    @get:Column(name = "ref_leaf_idx", nullable = false)
    var refLeafIndex: Int,

    @get:Column(name = "is_ref_input", nullable = false)
    var isRefInput: Boolean,

    @get:Column(name = "created", nullable = false)
    var created: Instant
)

@Embeddable
data class UtxoTransactionSourceEntityId(
    var transaction: UtxoTransactionEntity,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable
