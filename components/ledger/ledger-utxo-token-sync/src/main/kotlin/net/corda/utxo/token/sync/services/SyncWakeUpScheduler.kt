package net.corda.utxo.token.sync.services

import net.corda.data.ledger.utxo.token.selection.data.TokenSyncWakeUp
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener

/**
 * The [SyncWakeUpScheduler] is listens to [TokenSyncState] changes and schedules periodic [TokenSyncWakeUp] events.
 */
interface SyncWakeUpScheduler : StateAndEventListener<String, TokenSyncState> {

    /**
     * Called when the worker configuration changes.
     *
     * @param config map of the worker's configuration sections
     */
    fun onConfigChange(config: Map<String, SmartConfig>)
}
