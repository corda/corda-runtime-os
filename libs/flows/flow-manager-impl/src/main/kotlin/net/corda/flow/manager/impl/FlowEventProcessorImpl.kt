package net.corda.flow.manager.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.exceptions.FlowHospitalException
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.flow.fiber.requests.FlowIORequest
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class FlowEventProcessorImpl(
    private val flowRunner: FlowRunner,
    flowEventHandlers: List<FlowEventHandler<Any>>,
    flowRequestHandlers: List<FlowRequestHandler<FlowIORequest<*>>>
) : FlowEventProcessor {

    private companion object {
        val log = contextLogger()
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java

    private val flowEventHandlers: Map<Any, FlowEventHandler<Any>> = flowEventHandlers.associateBy { it.type }

    private val flowRequestHandlers: Map<Class<FlowIORequest<*>>, FlowRequestHandler<FlowIORequest<*>>> =
        flowRequestHandlers.associateBy { it.type }

    override fun onNext(state: Checkpoint?, event: Record<FlowKey, FlowEvent>): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value ?: throw FlowHospitalException("FlowEvent was null")
        log.info("Received event: ${flowEvent.payload::class.java} / ${flowEvent.payload}")
        return try {
            startPipeline(state, flowEvent)
                .eventPreProcessing()
                .runOrContinue()
                .setCheckpointSuspendedOn()
                .requestPostProcessing()
                .eventPostProcessing()
                .toStateAndEventResponse()
        } catch (e: FlowProcessingException) {
            log.error("Error processing flow event $event", e)
            StateAndEventProcessor.Response(state, emptyList())
        }
    }

    private fun startPipeline(checkpoint: Checkpoint?, event: FlowEvent): FlowEventPipeline {
        val context = FlowEventContext<Any>(
            checkpoint = checkpoint,
            inputEvent = event,
            inputEventPayload = event.payload,
            outputRecords = emptyList()
        )
        return FlowEventPipeline(getFlowEventHandler(event), flowRequestHandlers, flowRunner, context)
    }

    private fun getFlowEventHandler(event: FlowEvent): FlowEventHandler<Any> {
        return when (val handler = flowEventHandlers[event.payload::class.java]) {
            null -> throw FlowProcessingException("${event.payload::class.java.name} does not have an associated flow event handler")
            else -> handler
        }
    }
}
