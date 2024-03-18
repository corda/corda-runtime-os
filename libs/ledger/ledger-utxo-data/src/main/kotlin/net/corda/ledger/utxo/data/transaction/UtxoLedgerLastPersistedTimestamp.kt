package net.corda.ledger.utxo.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant

@CordaSerializable
data class UtxoLedgerLastPersistedTimestamp(
    val lastPersistedTimestamp: Instant
)
