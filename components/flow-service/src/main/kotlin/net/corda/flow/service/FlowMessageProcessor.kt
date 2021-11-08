package net.corda.flow.service

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.service.exception.FlowHospitalException
import net.corda.flow.service.exception.FlowMessageSkipException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.sandbox.service.SandboxService

class FlowMessageProcessor(
    private val flowManager: FlowManager,
    private val sandboxService: SandboxService,
    private val flowEventTopic: String,
) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value ?: throw FlowMessageSkipException("FlowEvent was null")
        val flowKey = event.key
        val cpiId = flowEvent.cpiId
        val identity = flowKey.identity.x500Name
        val result = when (val payload = flowEvent.payload) {
            is StartRPCFlow -> {
                val flowName = payload.flowName
                if (state != null) {
                    throw FlowMessageSkipException("State should be null for StartRPCFlow. Duplicate message.")
                }
                val sandboxGroup = sandboxService.getSandboxGroupFor(cpiId, identity, flowName)
                val checkpointSerializer = sandboxService.getSerializerForSandbox(sandboxGroup)
                flowManager.startInitiatingFlow(
                    FlowMetaData(flowName, flowKey, payload.jsonArgs, cpiId, flowEventTopic),
                    payload.clientId,
                    sandboxGroup,
                    checkpointSerializer
                )
            }
            is Wakeup -> {
                val checkpoint = state ?: throw FlowHospitalException("State for wakeup FlowEvent was null")
                val checkpointSerializer =
                    sandboxService.getSerializerForSandbox(sandboxService.getSandboxGroupFor(cpiId, identity, payload.flowName))
                flowManager.wakeFlow(checkpoint, flowEvent, flowEventTopic, checkpointSerializer)
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
