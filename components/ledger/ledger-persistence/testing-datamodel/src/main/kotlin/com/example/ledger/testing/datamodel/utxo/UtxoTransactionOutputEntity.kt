package com.example.ledger.testing.datamodel.utxo

import net.corda.v5.base.annotations.CordaSerializable
import java.io.Serializable
import java.math.BigDecimal
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
    name = "UtxoTransactionOutputEntity.findByTransactionId",
    query = "from UtxoTransactionOutputEntity where transaction.id = :transactionId"
)
@Entity
@Table(name = "utxo_transaction_output")
@IdClass(UtxoTransactionOutputEntityId::class)
data class UtxoTransactionOutputEntity(
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

    @get:Column(name = "type", nullable = true)
    var type: String?,

    @get:Column(name = "token_type", nullable = true)
    var tokenType: String?,

    @get:Column(name = "token_issuer_hash", nullable = true)
    var tokenIssuerHash: String?,

    @get:Column(name = "token_notary_x500_name", nullable = true)
    var tokenNotaryX500Name: String?,

    @get:Column(name = "token_symbol", nullable = true)
    var tokenSymbol: String?,

    @get:Column(name = "token_tag", nullable = true)
    var tokenTag: String?,

    @get:Column(name = "token_owner_hash", nullable = true)
    var tokenOwnerHash: String?,

    @get:Column(name = "token_amount", nullable = true)
    var tokenAmount: BigDecimal?,

    @get:Column(name = "created", nullable = false)
    var created: Instant
)

@Embeddable
data class UtxoTransactionOutputEntityId(
    var transaction: UtxoTransactionEntity,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable
