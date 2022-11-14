package net.corda.utxo.token.sync.converters

import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.utxo.token.sync.entities.SyncRequest

interface EventConverter {
    fun convert(tokenSyncEvent: TokenSyncEvent?): SyncRequest
}
