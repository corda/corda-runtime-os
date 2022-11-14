package net.corda.utxo.token.sync.handlers

import net.corda.messaging.api.records.Record
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.entities.SyncRequest

/**
 * The [SyncRequestHandler] represents the action to be taken when a specific request type is received.
 */
interface SyncRequestHandler<E : SyncRequest> {

    /**
     * Handles the received event
     *
     * @param state An instance of the [CurrentSyncState] linked to this event
     * @param request The received request.
     */
    fun handle(
        state: CurrentSyncState,
        request: E
    ): List<Record<*, *>>
}

