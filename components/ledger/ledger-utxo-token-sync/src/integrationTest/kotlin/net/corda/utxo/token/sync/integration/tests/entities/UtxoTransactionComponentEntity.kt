package net.corda.utxo.token.sync.integration.tests.entities

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "utxo_transaction_component")
class UtxoTransactionComponentEntity(
    @Id
    @Column(name = "transaction_id", nullable = false)
    val transactionId: String,

    @Id
    @Column(name = "group_idx", nullable = false)
    val groupIdx: Int,

    @Id
    @Column(name = "leaf_idx", nullable = false)
    val leafIdx: Int,

    @Column(name = "data", nullable = false)
    val data: ByteArray,

    @Column(name = "hash", nullable = false)
    val hash: String,

    @Column(name = "created", nullable = false)
    val created: Instant
) : Serializable