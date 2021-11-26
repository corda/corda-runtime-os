package net.corda.flow.service

import net.corda.data.flow.FlowInfo
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
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
) : StateAndEventProcessor<FlowInfo, Checkpoint, FlowEvent> {

    companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowInfo, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEventTopic = event.topic
        val flowEvent = event.value ?: throw FlowHospitalException("FlowEvent was null")
        val flowInfo = event.key
        val cpiId = flowInfo.cpiId
        val identity = flowInfo.identity.x500Name
        val result = when (val payload = flowEvent.payload) {
            is StartRPCFlow -> {
                val flowName = payload.flowName
                if (state != null) {
                    logger.warn("Skipping record with key ${event.key}")
                    FlowResult(state, emptyList())
                } else {
                    val sandboxGroup = sandboxService.getSandboxGroupFor(cpiId, identity, SandboxType.FLOW)
                    flowManager.startInitiatingFlow(
                        FlowMetaData(flowName, flowInfo, payload.jsonArgs, cpiId, flowEventTopic),
                        payload.clientId,
                        sandboxGroup
                    )
                }
            }
            is Wakeup -> {
                val checkpoint = state ?: throw FlowHospitalException("State for wakeup FlowEvent was null")
                flowManager.wakeFlow(
                    checkpoint,
                    flowEvent,
                    flowEventTopic,
                    sandboxService.getSandboxGroupFor(cpiId, identity, SandboxType.FLOW)
                )
            }
            else -> {
                throw NotImplementedError()
            }
        }
        return StateAndEventProcessor.Response(result.checkpoint, result.events)
    }

    override val keyClass = FlowInfo::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}
