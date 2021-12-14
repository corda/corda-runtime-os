package net.corda.flow.manager.impl

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.flow.statemachine.FlowContinuation
import net.corda.flow.statemachine.requests.FlowIORequest
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.v5.base.util.contextLogger

data class FlowEventPipeline(
    val flowEventHandler: FlowEventHandler<Any>,
    val flowRequestHandlers: Map<Class<FlowIORequest<*>>, FlowRequestHandler<FlowIORequest<*>>>,
    val flowRunner: FlowRunner,
    val context: FlowEventContext<Any>,
    val input: FlowContinuation = FlowContinuation.Continue,
    val output: FlowIORequest<*>? = null
) {
    private companion object {
        val log = contextLogger()
    }

    fun eventPreProcessing(): FlowEventPipeline {
        log.info("Preprocessing of ${context.inputEventPayload::class.java.name} using ${flowEventHandler::class.java.name}")
        return copy(context = flowEventHandler.preProcess(context))
    }

    fun runOrContinue(): FlowEventPipeline {
        log.info("Should resume or continue after receiving ${context.inputEventPayload::class.java.name} using ${flowEventHandler::class.java.name}")
        return when (val outcome = flowEventHandler.resumeOrContinue(context)) {
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

    fun setCheckpointSuspendedOn(): FlowEventPipeline {
        context.checkpoint!!.flowState.suspendedOn = output!!::class.qualifiedName
        return this
    }

    fun requestPostProcessing(): FlowEventPipeline {
        // If the flow fiber did not run or resume then there is no request post processing to execute.
        return if (input == FlowContinuation.Continue) {
            this
        } else {
            log.info("Postprocessing of $output")
            copy(context = getFlowRequestHandler(output!!).postProcess(context, output))
        }
    }

    fun eventPostProcessing(): FlowEventPipeline {
        log.info("Postprocessing of ${context.inputEventPayload::class.java.name} using ${flowEventHandler::class.java.name}")
        return copy(context = flowEventHandler.postProcess(context))
    }

    fun toStateAndEventResponse(): StateAndEventProcessor.Response<Checkpoint> {
        log.info("Sending output records to message bus: ${context.outputRecords}")
        return StateAndEventProcessor.Response(context.checkpoint, context.outputRecords)
    }

    private fun getFlowRequestHandler(request: FlowIORequest<*>): FlowRequestHandler<FlowIORequest<*>> {
        return when (val handler = flowRequestHandlers[request::class.java]) {
            null -> throw FlowProcessingException("${request::class.java.name} does not have an associated flow request handler")
            else -> handler
        }
    }
}