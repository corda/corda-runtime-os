package net.corda.utxo.token.sync.integration.tests.entities

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "utxo_transaction")
class UtxoTransactionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: String,

    @Column(name = "privacy_salt", nullable = false)
    val privacySalt: ByteArray,

    @Column(name = "account_id", nullable = false)
    val accountId: String,

    @Column(name = "created", nullable = false)
    val created: Instant,
)


