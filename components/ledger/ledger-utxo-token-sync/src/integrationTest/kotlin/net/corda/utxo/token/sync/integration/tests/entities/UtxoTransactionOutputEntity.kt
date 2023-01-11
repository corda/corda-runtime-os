package net.corda.utxo.token.sync.integration.tests.entities

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "utxo_transaction_output")
class UtxoTransactionOutputEntity(
    @Id
    @Column(name = "transaction_id", nullable = false)
    val transactionId: String,

    @Id
    @Column(name = "group_idx", nullable = false)
    val groupIdx: Int,

    @Id
    @Column(name = "leaf_idx", nullable = false)
    val leafIdx: Int,

    @Column(name = "type", nullable = false)
    val type: String,

    @Column(name = "token_type", nullable = false)
    val tokenType: String,

    @Column(name = "token_issuer_hash", nullable = false)
    val tokenIssuerHash: String,

    @Column(name = "token_notary_x500_name", nullable = false)
    val tokenNotaryX500Name: String,

    @Column(name = "token_symbol", nullable = false)
    val tokenSymbol: String,

    @Column(name = "token_tag", nullable = true)
    val tokenTag: String?,

    @Column(name = "token_owner_hash", nullable = true)
    val tokenOwnerHash: String?,

    @Column(name = "token_amount", nullable = false)
    val tokenAmount: BigDecimal,

    @Column(name = "created", nullable = false)
    val created: Instant,
):Serializable