package net.cordapp.testing.utxo.db

import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/**
 * An incoming chat message, one received by this member sent from another.
 */
@CordaSerializable
@Entity
data class Ledger(
    @Id
    @Column
    val txid: Long,
    @Column
    val block: ByteArray
)
