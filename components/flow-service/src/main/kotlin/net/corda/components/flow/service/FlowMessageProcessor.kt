package net.corda.components.flow.service

import net.corda.components.sandbox.service.SandboxService
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowMetaData
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record

class FlowMessageProcessor(
    private val flowManager: FlowManager,
    private val sandboxService: SandboxService,
    private val flowEventTopic: String,
) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value ?: throw CordaMessageAPIIntermittentException("FlowEvent was null")
        val result = when (val payload = flowEvent.payload) {
            is StartRPCFlow -> {
                val flowName = payload.flowName
                val cpiId = payload.cpiId
                val sandboxGroup = sandboxService.getSandboxGroupFor(payload.cpiId, flowName)
                flowManager.startInitiatingFlow(
                    FlowMetaData(flowName, event.key, payload.jsonArgs),
                    payload.clientId,
                    flowEventTopic,
                    cpiId,
                    sandboxGroup
                )
            }
            is Wakeup -> {
                val checkpoint = state ?: throw CordaMessageAPIIntermittentException("State for wakeup FlowEvent was null")
                val sandboxGroup = sandboxService.getSandboxGroupFor(payload.cpiId, payload.flowName)
                flowManager.wakeFlow(checkpoint, flowEvent, flowEventTopic, sandboxGroup)
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
