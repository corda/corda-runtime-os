package net.corda.ledger.utxo.token.cache.services

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.messaging.api.processor.SyncRPCProcessor

/**
 * In the future the legacy Kaka RPC path will be removed and the event processing pipelined merged into a single
 * HTTP path. For now both are supported.
 * CORE-17955
 */
class TokenSelectionSyncRPCProcessor(
    private val tokenSelectionDelegatedProcessor: TokenSelectionDelegatedProcessor
) : SyncRPCProcessor<TokenPoolCacheEvent, FlowEvent> {
    override fun process(request: TokenPoolCacheEvent): FlowEvent? {
        return tokenSelectionDelegatedProcessor.process(request)
    }

    override val requestClass = TokenPoolCacheEvent::class.java

    override val responseClass: Class<FlowEvent> = FlowEvent::class.java
}