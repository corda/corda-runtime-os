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
import net.corda.flow.statemachine.FlowContinuation
import net.corda.flow.statemachine.requests.FlowIORequest
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
        val context = FlowEventContext<Any>(
            checkpoint = state,
            inputEvent = flowEvent,
            inputEventPayload = flowEvent.payload,
            outputRecords = emptyList()
        )

        return try {
            FlowEventPipeline(context, getFlowEventHandler(flowEvent))
                .eventPreProcessing()
                .resumeOrContinue()
                .setCheckpointSuspendedOn()
                .requestPostProcessing()
                .eventPostProcessing()
                .toStateAndEventResponse()
        } catch (e: FlowProcessingException) {
            log.error("Error processing flow event $event", e)
            StateAndEventProcessor.Response(state, emptyList())
        }
    }

    private fun FlowEventPipeline.eventPreProcessing(): FlowEventPipeline {
        log.info("Preprocessing of ${context.inputEventPayload::class.java.name} using ${handler::class.java.name}")
        return copy(context = handler.preProcess(context))
    }

    private fun FlowEventPipeline.resumeOrContinue(): FlowEventPipeline {
        log.info("Should resume or continue after receiving ${context.inputEventPayload::class.java.name} using ${handler::class.java.name}")
        return when (val outcome = handler.resumeOrContinue(context)) {
            is FlowContinuation.Run, is FlowContinuation.Error -> {
                val (checkpoint, output) = flowRunner.runFlow(
                    context.checkpoint!!,
                    context.inputEvent,
                    outcome
                ).waitForCheckpoint()
                copy(context = context.copy(checkpoint = checkpoint), input = outcome, output = output)
            }
            is FlowContinuation.Continue -> copy(input = outcome)
        }
    }

    private fun FlowEventPipeline.setCheckpointSuspendedOn() : FlowEventPipeline {
        context.checkpoint!!.flowState.suspendedOn = output!!::class.qualifiedName
        return this
    }

    private fun FlowEventPipeline.requestPostProcessing(): FlowEventPipeline {
        // If the flow fiber did not run or resume then there is no request post processing to execute.
        return if (input == FlowContinuation.Continue) {
            this
        } else {
            log.info("Postprocessing of $output")
            copy(context = getFlowRequestHandler(output!!).postProcess(context, output))
        }
    }

    private fun FlowEventPipeline.eventPostProcessing(): FlowEventPipeline {
        log.info("Postprocessing of ${context.inputEventPayload::class.java.name} using ${handler::class.java.name}")
        return copy(context = handler.postProcess(context))
    }

    private fun FlowEventPipeline.toStateAndEventResponse(): StateAndEventProcessor.Response<Checkpoint> {
        log.info("Sending output records to message bus: ${context.outputRecords}")
        return StateAndEventProcessor.Response(context.checkpoint, context.outputRecords)
    }

    private fun getFlowEventHandler(event: FlowEvent): FlowEventHandler<Any> {
        return when (val handler = flowEventHandlers[event.payload::class.java]) {
            null -> throw FlowProcessingException("${event.payload::class.java.name} does not have an associated flow event handler")
            else -> handler
        }
    }

    private fun getFlowRequestHandler(request: FlowIORequest<*>): FlowRequestHandler<FlowIORequest<*>> {
        return when (val handler = flowRequestHandlers[request::class.java]) {
            null -> throw FlowProcessingException("${request::class.java.name} does not have an associated flow request handler")
            else -> handler
        }
    }
}
