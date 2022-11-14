package net.corda.utxo.token.sync.factories

import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * The [TokenCacheSyncRequestProcessorFactory] creates instances of the [StateAndEventProcessor]
 */
interface TokenCacheSyncRequestProcessorFactory {

    /**
     * Creates an instance of the  [StateAndEventProcessor]
     */
    fun create(): StateAndEventProcessor<String, TokenSyncState, TokenSyncEvent>
}
