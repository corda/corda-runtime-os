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

    @Column(name = "type", nullable = true)
    val type: String?,

    @Column(name = "token_type", nullable = true)
    val tokenType: String?,

    @Column(name = "token_issuer_hash", nullable = true)
    val tokenIssuerHash: String?,

    @Column(name = "token_notary_x500_name", nullable = true)
    val tokenNotaryX500Name: String?,

    @Column(name = "token_symbol", nullable = true)
    val tokenSymbol: String?,

    @Column(name = "token_tag", nullable = true)
    val tokenTag: String?,

    @Column(name = "token_owner_hash", nullable = true)
    val tokenOwnerHash: String?,

    @Column(name = "token_amount", nullable = true)
    val tokenAmount: BigDecimal?,

    @Column(name = "created", nullable = false)
    val created: Instant
)

@Embeddable
data class UtxoTransactionOutputEntityId(
    val transaction: UtxoTransactionEntity,
    val groupIndex: Int,
    val leafIndex: Int
) : Serializable