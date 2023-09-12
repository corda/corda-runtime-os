package com.example.ledger.testing.datamodel.utxo

import net.corda.v5.base.annotations.CordaSerializable
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Index
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.NamedQuery
import javax.persistence.Table

@CordaSerializable
@NamedQuery(
    name = "UtxoVisibleTransactionStateEntity.findByTransactionId",
    query = "from UtxoVisibleTransactionStateEntity where transaction.id = :transactionId"
)
@Entity
@Table(name = "utxo_visible_transaction_state", indexes = [Index(name = "consumed_index", columnList = "consumed")])
@IdClass(UtxoVisibleTransactionStateEntityId::class)
data class UtxoVisibleTransactionStateEntity(
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

    @get:Column(name = "custom_representation", nullable = false, columnDefinition = "jsonb")
    var customRepresentation: String,

    @get:Column(name = "created", nullable = false)
    var created: Instant,

    @get:Column(name = "consumed", nullable = true)
    var consumed: Instant?
)

@Embeddable
data class UtxoVisibleTransactionStateEntityId(
    var transaction: UtxoTransactionEntity,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable
