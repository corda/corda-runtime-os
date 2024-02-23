package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.FlowEngineReplayService
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowMDCService
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.handlers.FlowPostProcessingHandler
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.MediatorInputService.Companion.INPUT_HASH_HEADER
import net.corda.messaging.api.mediator.MediatorInputService.Companion.SYNC_RESPONSE_HEADER
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.State
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.tracing.joinExecution
import net.corda.tracing.traceStateAndEventExecution
import net.corda.utilities.debug
import net.corda.utilities.retry.Exponential
import net.corda.utilities.retry.tryWithBackoff
import net.corda.utilities.trace
import net.corda.utilities.withMDC
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class FlowEventProcessorImpl(
    private val flowEventPipelineFactory: FlowEventPipelineFactory,
    private val flowEventExceptionProcessor: FlowEventExceptionProcessor,
    private val flowEventContextConverter: FlowEventContextConverter,
    private val configs: Map<String, SmartConfig>,
    private val flowMDCService: FlowMDCService,
    private val postProcessingHandlers: List<FlowPostProcessingHandler>,
    private val flowEngineReplayService: FlowEngineReplayService
) : StateAndEventProcessor<String, Checkpoint, FlowEvent> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass = String::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java

    private val flowConfig = configs[FLOW_CONFIG] ?: throw IllegalArgumentException("Flow config could not be found.")

    init {
        // This works for now, but we should consider introducing a provider we could then inject it into
        // the classes that need it rather than passing it through all the layers.
        flowEventExceptionProcessor.configure(flowConfig)
    }

    override fun onNext(
        state: State<Checkpoint>?,
        event: Record<String, FlowEvent>,
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value
        val mdcProperties = flowMDCService.getMDCLogging(state?.value, flowEvent, event.key)
        val sync = event.headers.any { it.first == SYNC_RESPONSE_HEADER }
        val eventType = event.value?.payload?.javaClass?.simpleName ?: "Unknown"
        return withMDC(mdcProperties) {
            when (sync) {
                true ->
                    joinExecution(event, "Flow Event - $eventType" ) {
                        createAndExecutePipeline(event, state, mdcProperties)
                    }
                false -> {
                    traceStateAndEventExecution(event, "Flow Event - $eventType") {
                        createAndExecutePipeline(event, state, mdcProperties)
                    }
                }
            }
        }
    }

    private fun createAndExecutePipeline(
        event: Record<String, FlowEvent>,
        state: State<Checkpoint>?,
        mdcProperties: Map<String, String>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value
        if (flowEvent == null) {
            log.debug { "The incoming event record '${event}' contained a null FlowEvent, this event will be discarded" }
            return StateAndEventProcessor.Response(
                state,
                listOf()
            )
        }

        val inputEventHash = getInputEventHash(event)
        if (!isSyncResponse(event)) {
            flowEngineReplayService.getReplayEvents(inputEventHash, state?.value)?.let { replays ->
                log.debug { "Detected input event that has been processed previously for hash :$inputEventHash" }
                return StateAndEventProcessor.Response(state, replays)
            }
        }

        val pipeline = try {
            log.trace { "Flow [${event.key}] Received event: ${flowEvent.payload::class.java} / ${flowEvent.payload}" }
            flowEventPipelineFactory.create(state, flowEvent, configs, mdcProperties,  event.timestamp, inputEventHash)
        } catch (t: Throwable) {
            log.warn("Failed to create flow event pipeline", t)
            //traceContext.error(CordaRuntimeException(t.message, t))
            // Without a pipeline there's a limit to what can be processed.
            return StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = listOf(),
                markForDLQ = true
            )
        }

        return executePipeline(pipeline)
    }

    private fun executePipeline(
        pipeline: FlowEventPipeline
    ): StateAndEventProcessor.Response<Checkpoint> {
        // flow result timeout must be lower than the processor timeout as the processor thread will be killed by the subscription consumer
        // thread after this period and so this timeout would never be reached and given a chance to return otherwise.
        val flowTimeout = (flowConfig.getLong(PROCESSOR_TIMEOUT) * 0.75).toLong()
        val result = try {
            tryWithBackoff(
                logger = log,
                maxRetries = flowConfig.getLong(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS),
                maxTimeMillis = flowConfig.getLong(FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION),
                // Exponential Backoff + Default maxRetryAttempts -> 1s, 2s, 4s, 8s, 16s = 31s max
                backoffStrategy = Exponential(base = 2.0, growthFactor = 500L),
                // Only FlowTransientException will be retried
                shouldRetry = { _, _, t -> t is FlowTransientException },
                onRetryAttempt = { n, d, t -> logRetryAndRollbackCheckpoint(pipeline.context.checkpoint, n, d, t) },
                onRetryExhaustion = { r, e, t -> giveUpAndThrowFlowFatalException(r, e, t) },
            ) {
                pipeline
                    .eventPreProcessing()
                    .virtualNodeFlowOperationalChecks()
                    .executeFlow(flowTimeout)
                    .globalPostProcessing()
                    .context
            }
        } catch (e: FlowEventException) {
            flowEventExceptionProcessor.process(e, pipeline.context)
        } catch (e: FlowPlatformException) {
            flowEventExceptionProcessor.process(e, pipeline.context)
        } catch (e: FlowFatalException) {
            flowEventExceptionProcessor.process(e, pipeline.context)
        } catch (e: FlowMarkedForKillException) {
            flowEventExceptionProcessor.process(e, pipeline.context)
        } catch (t: Throwable) {
            flowEventExceptionProcessor.process(t, pipeline.context)
        }

        val cleanupEvents = mutableListOf<Record<*, *>>()

        for (postProcessingHandler in postProcessingHandlers) {
            try {
                cleanupEvents.addAll(postProcessingHandler.postProcess(pipeline.context))
            } catch (e: Exception) {
                log.error(
                    "The flow event post processing handler '${postProcessingHandler::javaClass}' failed and will be ignored.",
                    e
                )
            }
        }

        //Save outputs to replay in the future. This must be the last step after all processing
        val outputs = result.outputRecords + cleanupEvents
        val hash = result.inputEventHash
        if (hash != null) {
            result.checkpoint.saveOutputs(flowEngineReplayService.generateSavedOutputs(hash, outputs))
        }
        return flowEventContextConverter.convert(
            result.copy(outputRecords = outputs)
        )
    }

    private fun getInputEventHash(event: Record<String, FlowEvent>) =
        event.headers.find { it.first == INPUT_HASH_HEADER }?.second ?: throw IllegalStateException("Record with key ${event.key} is " +
                "missing expected record header '$INPUT_HASH_HEADER'")

    private fun isSyncResponse(event: Record<String, FlowEvent>) =
        event.headers.find { it.first == SYNC_RESPONSE_HEADER }?.second == "true"

    /**
     * Executed within the [tryWithBackoff] function whenever a retry attempt is about to be made.
     * We simply log the attempt under INFO level and rollback the checkpoint.
     *
     * @param flowCheckpoint current flow checkpoint.
     * @param retryNumber retry attempt number, starting in 1.
     * @param delayMillis delay before the next retry attempt is made.
     * @param throwable original exception thrown while executing retry attempt number [retryNumber].
     */
    private fun logRetryAndRollbackCheckpoint(
        flowCheckpoint: FlowCheckpoint,
        retryNumber: Int, delayMillis: Long, throwable: Throwable
    ) {
        log.info(
            "Flow ${flowCheckpoint.flowId} encountered a transient error (attempt $retryNumber) and will retry " +
                "after $delayMillis milliseconds: ${throwable.message}"
        )
        flowCheckpoint.rollback()
    }

    /**
     * Executed within the [tryWithBackoff] function when all retry attempts has been exhausted.
     *
     * @param retryCount total amount of retry attempts.
     * @param elapsedTime total amount of time spent retrying the transient exception.
     * @param throwable original exception thrown while executing the last retry attempt.
     */
    private fun giveUpAndThrowFlowFatalException(
        retryCount: Int, elapsedTime: Long, throwable: Throwable
    ): CordaRuntimeException {
        return FlowFatalException(
            "Execution failed with \"${throwable.message}\" after $retryCount retry attempts in a " +
                "retry window of $elapsedTime milliseconds.",
            throwable
        )
    }
}
