package net.corda.flow.service

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.scheduler.FlowWakeUpScheduler
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowExecutorRebalanceListener::class])
class FlowExecutorRebalanceListener @Activate constructor(
    @Reference(service = FlowWakeUpScheduler::class)
    private val flowWakeUpScheduler: FlowWakeUpScheduler,
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache
) : StateAndEventListener<String, Checkpoint> {
    override fun onPartitionSynced(states: Map<String, Checkpoint>) {
        flowWakeUpScheduler.onPartitionSynced(states)
    }

    override fun onPartitionLost(states: Map<String, Checkpoint>) {
        flowWakeUpScheduler.onPartitionLost(states)
        handleFlowFiberCachePartitionLost(states)
    }

    override fun onPostCommit(updatedStates: Map<String, Checkpoint?>) {
        flowWakeUpScheduler.onPostCommit(updatedStates)
    }

    private fun handleFlowFiberCachePartitionLost(states: Map<String, Checkpoint>) {
        val holdingIdentitiesLost = states.map { it.value.flowState.flowStartContext.identity.toCorda() }.toSet()
        flowFiberCache.remove(holdingIdentitiesLost)
    }
}