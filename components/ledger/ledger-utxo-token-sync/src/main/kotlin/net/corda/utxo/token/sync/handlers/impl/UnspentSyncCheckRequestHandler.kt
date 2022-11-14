package net.corda.utxo.token.sync.handlers.impl

import net.corda.messaging.api.records.Record
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.entities.UnspentSyncCheckRequest
import net.corda.utxo.token.sync.factories.MessagingRecordFactory
import net.corda.utxo.token.sync.handlers.SyncRequestHandler
import net.corda.utxo.token.sync.services.SyncService

class UnspentSyncCheckRequestHandler(
    private val syncService: SyncService,
    private val messagingRecordConverter: MessagingRecordFactory,
) : SyncRequestHandler<UnspentSyncCheckRequest> {
    override fun handle(state: CurrentSyncState, request: UnspentSyncCheckRequest): List<Record<*, *>> {
        val spentTokens = syncService.validateUnspentTokens(request.holdingIdentity, request.tokensToCheck)

        // We can batch the results by key to minimise the number of records we need to publish
        val recordsByKey = spentTokens.groupBy { it.key }

        return recordsByKey.map {
            messagingRecordConverter.createTokenPoolCacheEvent(
                holdingIdentity = state.holdingIdentity,
                key = it.key,
                unspentTokens = listOf(),
                spentTokens = it.value
            )
        }
    }
}
