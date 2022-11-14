package net.corda.utxo.token.sync.handlers.impl

import net.corda.messaging.api.records.Record
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.entities.CurrentSyncStateType
import net.corda.utxo.token.sync.entities.WakeUpSyncRequest
import net.corda.utxo.token.sync.factories.MessagingRecordFactory
import net.corda.utxo.token.sync.handlers.SyncRequestHandler
import net.corda.utxo.token.sync.services.SyncService

class WakeUpHandler(
    private val syncService: SyncService,
    private val messagingRecordConverter: MessagingRecordFactory,
) : SyncRequestHandler<WakeUpSyncRequest> {

    override fun handle(state: CurrentSyncState, request: WakeUpSyncRequest): List<Record<*, *>> {
        return if (state.mode == CurrentSyncStateType.FULL_SYNC) {
            syncService
                .getNextFullSyncBlock(state)
                .groupBy { it.key }
                .map {
                    messagingRecordConverter.createTokenPoolCacheEvent(
                        holdingIdentity = state.holdingIdentity,
                        key = it.key,
                        unspentTokens = it.value,
                        spentTokens = listOf()
                    )
                }
        } else {
            syncService
                .getNextPeriodCheckBlock(state)
                .map {
                    messagingRecordConverter.createSyncCheckEvent(
                        holdingIdentity = state.holdingIdentity,
                        key = it.key,
                        unspentTokens = it.value
                    )
                }
        }
    }
}
