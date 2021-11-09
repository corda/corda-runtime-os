package net.corda.flow.service

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.manager.FlowResult
import net.corda.flow.service.exception.FlowHospitalException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.sandbox.service.SandboxService
import net.corda.sandbox.service.SandboxType
import net.corda.v5.base.util.contextLogger

class FlowMessageProcessor(
    private val flowManager: FlowManager,
    private val sandboxService: SandboxService
) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEventTopic = event.topic
        try {
            val flowEvent = event.value ?: throw FlowHospitalException("FlowEvent was null")
            val flowKey = event.key
            val cpiId = flowEvent.cpiId
            val identity = flowKey.identity.x500Name
            val result = when (val payload = flowEvent.payload) {
                is StartRPCFlow -> {
                    val flowName = payload.flowName
                    if (state != null) {
                        logger.warn("Skipping record with key ${event.key}")
                        FlowResult(state, emptyList())
                    } else {
                        val sandboxGroup = sandboxService.getSandboxGroupFor(cpiId, identity, SandboxType.FLOW)
                        val checkpointSerializer = sandboxService.getSerializerForSandbox(sandboxGroup)
                        flowManager.startInitiatingFlow(
                            FlowMetaData(flowName, flowKey, payload.jsonArgs, cpiId, flowEventTopic),
                            payload.clientId,
                            sandboxGroup,
                            checkpointSerializer
                        )
                    }
                }
                is Wakeup -> {
                    val checkpoint = state ?: throw FlowHospitalException("State for wakeup FlowEvent was null")
                    val checkpointSerializer =
                        sandboxService.getSerializerForSandbox(sandboxService.getSandboxGroupFor(cpiId, identity, SandboxType.FLOW))
                    flowManager.wakeFlow(checkpoint, flowEvent, flowEventTopic, checkpointSerializer)
                }
                else -> {
                    throw NotImplementedError()
                }
            }
            return StateAndEventProcessor.Response(result.checkpoint, result.events)
        } catch (ex: FlowHospitalException) {
            //TODO - how do we dead letter queue this? i.e put it into the flow hospital
            return StateAndEventProcessor.Response(null, emptyList())
        }
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}
