package net.corda.flow.manager.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.flow.statemachine.FlowContinuation
import net.corda.flow.statemachine.requests.FlowIORequest
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.v5.base.util.contextLogger

class FlowEventPipelineImpl(
    private val flowRunner: FlowRunner,
    flowEventHandlers: List<FlowEventHandler<Any>>,
    flowRequestHandlers: List<FlowRequestHandler<FlowIORequest<*>>>
) : FlowEventPipeline {

    private companion object {
        val log = contextLogger()
    }

    private val flowEventHandlers: Map<Any, FlowEventHandler<Any>> = flowEventHandlers.associateBy { it.type }

    private val flowRequestHandlers: Map<Class<FlowIORequest<*>>, FlowRequestHandler<FlowIORequest<*>>> =
        flowRequestHandlers.associateBy { it.type }

    override fun start(checkpoint: Checkpoint?, event: FlowEvent): FlowEventPipelineContext {
        val context = FlowEventContext<Any>(
            checkpoint = checkpoint,
            inputEvent = event,
            inputEventPayload = event.payload,
            outputRecords = emptyList()
        )
        return FlowEventPipelineContext(context, getFlowEventHandler(event))
    }

    override fun eventPreProcessing(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext {
        return pipelineContext.run {
            log.info("Preprocessing of ${context.inputEventPayload::class.java.name} using ${handler::class.java.name}")
            copy(context = handler.preProcess(context))
        }
    }

    override fun runOrContinue(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext {
        return pipelineContext.run {
            log.info("Should resume or continue after receiving ${context.inputEventPayload::class.java.name} using ${handler::class.java.name}")
            when (val outcome = handler.resumeOrContinue(context)) {
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
    }

    override fun setCheckpointSuspendedOn(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext {
        return pipelineContext.run {
            context.checkpoint!!.flowState.suspendedOn = output!!::class.qualifiedName
            this
        }
    }

    override fun requestPostProcessing(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext {
        return pipelineContext.run {
            // If the flow fiber did not run or resume then there is no request post processing to execute.
            if (input == FlowContinuation.Continue) {
                this
            } else {
                log.info("Postprocessing of $output")
                copy(context = getFlowRequestHandler(output!!).postProcess(context, output))
            }
        }
    }

    override fun eventPostProcessing(pipelineContext: FlowEventPipelineContext): FlowEventPipelineContext {
        return pipelineContext.run {
            log.info("Postprocessing of ${context.inputEventPayload::class.java.name} using ${handler::class.java.name}")
            copy(context = handler.postProcess(context))
        }
    }

    override fun toStateAndEventResponse(pipelineContext: FlowEventPipelineContext): StateAndEventProcessor.Response<Checkpoint> {
        return pipelineContext.run {
            log.info("Sending output records to message bus: ${context.outputRecords}")
            StateAndEventProcessor.Response(context.checkpoint, context.outputRecords)
        }
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