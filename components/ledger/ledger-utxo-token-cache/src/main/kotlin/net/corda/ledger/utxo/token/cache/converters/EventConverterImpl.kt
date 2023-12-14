package net.corda.ledger.utxo.token.cache.converters

import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenForceClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.ledger.utxo.token.cache.entities.TokenEvent

class EventConverterImpl(private val entityConverter: EntityConverter) : EventConverter {

    override fun convert(tokenPoolCacheEvent: TokenPoolCacheEvent): TokenEvent {
        val key = tokenPoolCacheEvent.poolKey

        return when (
            val payload =
                checkNotNull(tokenPoolCacheEvent.payload) { "The received TokenPoolCacheEvent payload is null." }
        ) {
            is TokenClaimQuery -> {
                entityConverter.toClaimQuery(key, payload)
            }

            is TokenClaimRelease -> {
                entityConverter.toClaimRelease(key, payload)
            }

            is TokenForceClaimRelease -> {
                entityConverter.toForceClaimRelease(key, payload)
            }

            is TokenBalanceQuery -> {
                entityConverter.toBalanceQuery(key, payload)
            }

            is TokenLedgerChange -> {
                entityConverter.toLedgerChange(key, payload)
            }

            else -> {
                error("The event payload type '${payload.javaClass}' is not supported. Found in event '$tokenPoolCacheEvent'")
            }
        }
    }
}
