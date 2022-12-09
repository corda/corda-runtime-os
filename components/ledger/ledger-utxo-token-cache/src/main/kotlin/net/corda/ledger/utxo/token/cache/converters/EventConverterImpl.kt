package net.corda.ledger.utxo.token.cache.converters

import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.data.TokenTestLedgerChange
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.ledger.utxo.token.cache.entities.TokenEvent

class EventConverterImpl(private val entityConverter: EntityConverter) : EventConverter {

    override fun convert(tokenPoolCacheEvent: TokenPoolCacheEvent?): TokenEvent {
        val event = checkNotNull(tokenPoolCacheEvent) { "The received TokenPoolCacheEvent is null." }
        val key = event.poolKey

        return when (val payload =
            checkNotNull(event.payload) { "The received TokenPoolCacheEvent payload is null." }) {
            is TokenClaimQuery -> {
                entityConverter.toClaimQuery(key, payload)
            }

            is TokenClaimRelease -> {
                entityConverter.toClaimRelease(key, payload)
            }

            is TokenLedgerChange -> {
                entityConverter.toLedgerChange(key, payload)
            }

            is TokenTestLedgerChange -> {
                entityConverter.toTestLedgerChange(key, payload)
            }

            else -> {
                error("The event payload type '${payload.javaClass}' is not supported. Found in event '${tokenPoolCacheEvent}'")
            }
        }
    }
}

