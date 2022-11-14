package net.corda.utxo.token.sync.services.impl

import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.Clock
import net.corda.utxo.token.sync.factories.MessagingRecordFactory
import net.corda.utxo.token.sync.services.SyncWakeUpScheduler
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SyncWakeUpSchedulerImpl constructor(
    private val publisherFactory: PublisherFactory,
    private val flowRecordFactory: MessagingRecordFactory,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val clock: Clock
) : SyncWakeUpScheduler {
    private val scheduledWakeUps = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private var publisher: Publisher? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        publisher?.close()
        publisher = null
        publisher = publisherFactory.createPublisher(
            PublisherConfig("TokenCacheSyncWakeUpSchedulerService", true),
            config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
    }

    override fun onPartitionSynced(states: Map<String, TokenSyncState>) {
        scheduleTasks(states.values)
    }

    override fun onPartitionLost(states: Map<String, TokenSyncState>) {
        cancelScheduledWakeUps(states.keys)
    }

    override fun onPostCommit(updatedStates: Map<String, TokenSyncState?>) {
        val updates = updatedStates.filter { it.value != null }.map { it.value!! }
        val deletes = updatedStates.filter { it.value == null }

        scheduleTasks(updates)
        cancelScheduledWakeUps(deletes.keys)
    }

    private fun scheduleTasks(syncStates: Collection<TokenSyncState>) {
        syncStates.forEach {
            val holdingId = it.holdingIdentity.toCorda()
            val delay = (it.nextWakeup.toEpochMilli() - clock.instant().toEpochMilli()).coerceAtLeast(0)
            val scheduledWakeUp = scheduledExecutorService.schedule(
                { publishWakeUp(holdingId) },
                delay,
                TimeUnit.MILLISECONDS
            )

            val existingWakeUp = scheduledWakeUps.put(holdingId.shortHash.toString(), scheduledWakeUp)
            existingWakeUp?.cancel(false)
        }
    }

    private fun cancelScheduledWakeUps(holdingIds:Collection<String>){
        holdingIds.forEach {
            scheduledWakeUps.remove(it)?.cancel(false)
        }
    }

    private fun publishWakeUp(holdingIdentity: HoldingIdentity) {
        publisher?.publish(listOf(flowRecordFactory.createSyncWakeup(holdingIdentity)))
    }
}
