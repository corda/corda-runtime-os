package net.corda.utxo.token.sync.handlers.impl

import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.entities.CurrentSyncStateType
import net.corda.utxo.token.sync.entities.FullSyncRequest
import net.corda.utxo.token.sync.factories.MessagingRecordFactory
import net.corda.utxo.token.sync.handlers.SyncRequestHandler
import net.corda.utxo.token.sync.services.SyncConfiguration
import net.corda.utxo.token.sync.services.SyncService

class FullSyncRequestHandler(
    private val configuration: SyncConfiguration,
    private val syncService: SyncService,
    private val messagingRecordConverter: MessagingRecordFactory,
    private val clock: Clock
) : SyncRequestHandler<FullSyncRequest> {

    override fun handle(state: CurrentSyncState, request: FullSyncRequest): List<Record<*, *>> {
        // If we're already running a full sync this request can be ignored
        if (state.mode == CurrentSyncStateType.FULL_SYNC) {
            return listOf()
        }

        // To prevent multiple full syncs running in a short space of time we wait for a configured period
        // of time after a successful run before we run another.
        val nextRunTime =
            state.getLastFullSyncCompletedTimestamp().plusSeconds(configuration.minDelayBeforeNextFullSync)
        if (nextRunTime > clock.instant()) {
            return listOf()
        }

        state.startFullSync()

        val initialRecordBatch = syncService.getNextFullSyncBlock(state)

        // We can batch the results by key to minimise the number of records we need to publish
        val recordsByKey = initialRecordBatch.groupBy { it.key }

        return recordsByKey.map {
            messagingRecordConverter.createTokenPoolCacheEvent(
                holdingIdentity = state.holdingIdentity,
                key = it.key,
                unspentTokens = it.value,
                spentTokens = listOf()
            )
        }
    }
}
