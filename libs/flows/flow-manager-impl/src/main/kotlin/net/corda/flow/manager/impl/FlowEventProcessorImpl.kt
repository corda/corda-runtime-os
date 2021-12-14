package net.corda.flow.manager.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.exceptions.FlowHospitalException
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class FlowEventProcessorImpl(private val flowEventPipeline: FlowEventPipeline) : FlowEventProcessor {

    private companion object {
        val log = contextLogger()
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java

    override fun onNext(state: Checkpoint?, event: Record<FlowKey, FlowEvent>): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value ?: throw FlowHospitalException("FlowEvent was null")
        log.info("Received event: ${flowEvent.payload::class.java} / ${flowEvent.payload}")
        return try {
            flowEventPipeline.run {
                var pipelineContext = start(state, flowEvent)
                pipelineContext = eventPreProcessing(pipelineContext)
                pipelineContext = runOrContinue(pipelineContext)
                pipelineContext = setCheckpointSuspendedOn(pipelineContext)
                pipelineContext = requestPostProcessing(pipelineContext)
                pipelineContext = eventPostProcessing(pipelineContext)
                toStateAndEventResponse(pipelineContext)
            }
        } catch (e: FlowProcessingException) {
            log.error("Error processing flow event $event", e)
            StateAndEventProcessor.Response(state, emptyList())
        }
    }
}
