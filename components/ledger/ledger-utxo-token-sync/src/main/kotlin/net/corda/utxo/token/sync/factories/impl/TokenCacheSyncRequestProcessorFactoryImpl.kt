package net.corda.utxo.token.sync.factories.impl

import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.utxo.token.sync.converters.EntityConverter
import net.corda.utxo.token.sync.converters.EventConverter
import net.corda.utxo.token.sync.entities.SyncRequest
import net.corda.utxo.token.sync.factories.TokenCacheSyncRequestProcessorFactory
import net.corda.utxo.token.sync.handlers.SyncRequestHandler
import net.corda.utxo.token.sync.services.impl.SyncRequestProcessor

class TokenCacheSyncRequestProcessorFactoryImpl constructor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val syncRequestHandlerMap: Map<Class<*>, SyncRequestHandler<in SyncRequest>>,
) : TokenCacheSyncRequestProcessorFactory {

    override fun create(): StateAndEventProcessor<String, TokenSyncState, TokenSyncEvent> {
        return SyncRequestProcessor(eventConverter, entityConverter, syncRequestHandlerMap)
    }
}
