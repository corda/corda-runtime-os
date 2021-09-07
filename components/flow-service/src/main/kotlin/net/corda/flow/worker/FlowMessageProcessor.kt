package net.corda.flow.worker

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.FlowManager
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.sandbox.cache.FlowMetadata

class FlowMessageProcessor(
    private val flowManager: FlowManager
) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val payload = event.value?.payload ?: throw IllegalArgumentException()
        val result = when (payload) {
            is StartRPCFlow -> {
                val flowName = payload.flowName
                flowManager.startInitiatingFlow(FlowMetadata(flowName, event.key), payload.clientId, payload.args)
            }
            is Wakeup -> {
                val checkpoint = state ?: throw IllegalArgumentException()
                flowManager.wakeFlow(checkpoint)
            }
            else -> {
                throw NotImplementedError()
            }
        }
        return StateAndEventProcessor.Response(result.checkpoint, result.events)
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}