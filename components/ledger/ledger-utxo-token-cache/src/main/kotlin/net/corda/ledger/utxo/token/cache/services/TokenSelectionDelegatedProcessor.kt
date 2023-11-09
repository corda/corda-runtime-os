package net.corda.ledger.utxo.token.cache.services

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent

/**
 * In the future the legacy Kaka RPC path will be removed and the event processing pipelined merged into a single
 * HTTP path. For now both are supported.
 * CORE-17955
 */
interface TokenSelectionDelegatedProcessor {
    fun process(request: TokenPoolCacheEvent): FlowEvent?
}