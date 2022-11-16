package net.corda.ledger.persistence.utxo

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.messaging.api.records.Record
import net.corda.v5.ledger.utxo.observer.UtxoToken

interface UtxoOutputRecordFactory {
    fun getTokenCacheChangeEventRecords(
        producedTokens: List<UtxoToken>,
        consumedTokens: List<UtxoToken>
    ): List<Record<TokenPoolCacheKey, TokenPoolCacheEvent>>
}
