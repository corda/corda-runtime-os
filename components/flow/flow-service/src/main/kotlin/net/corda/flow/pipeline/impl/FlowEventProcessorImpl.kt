package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.FlowMDCService
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.handlers.FlowPostProcessingHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.State
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.tracing.TraceContext
import net.corda.tracing.traceStateAndEventExecution
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.utilities.withMDC
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
@Suppress("LongParameterList")
class FlowEventProcessorImpl(
    private val flowEventPipelineFactory: FlowEventPipelineFactory,
    private val flowEventExceptionProcessor: FlowEventExceptionProcessor,
    private val flowEventContextConverter: FlowEventContextConverter,
    private val configs: Map<String, SmartConfig>,
    private val flowMDCService: FlowMDCService,
    private val postProcessingHandlers: List<FlowPostProcessingHandler>
) : StateAndEventProcessor<String, Checkpoint, FlowEvent> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        val eventType = event.value?.payload?.javaClass?.simpleName ?: "Unknown"
        return withMDC(mdcProperties) {
            traceStateAndEventExecution(event, "Flow Event - $eventType") {
                getFlowPipelineResponse(flowEvent, event, state, mdcProperties, this)
            }
        }
    }

    private fun getFlowPipelineResponse(
        flowEvent: FlowEvent?,
        event: Record<String, FlowEvent>,
        state: State<Checkpoint>?,
        mdcProperties: Map<String, String>,
        traceContext: TraceContext
    ): StateAndEventProcessor.Response<Checkpoint> {
        if (flowEvent == null) {
            log.debug { "The incoming event record '${event}' contained a null FlowEvent, this event will be discarded" }
            return StateAndEventProcessor.Response(
                state,
                listOf()
            )
        }

        val pipeline = try {
            log.trace { "Flow [${event.key}] Received event: ${flowEvent.payload::class.java} / ${flowEvent.payload}" }
            flowEventPipelineFactory.create(state, flowEvent, configs, mdcProperties, traceContext, event.timestamp)
        } catch (t: Throwable) {
            traceContext.error(CordaRuntimeException(t.message, t))
            // Without a pipeline there's a limit to what can be processed.
            return StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = listOf(),
                markForDLQ = true
            )
        }

        val checkpoint = state?.value
        val isInRetryState = pipeline.context.checkpoint.inRetryState && checkpoint?.flowState == null
        val flowEventPayload = flowEvent.payload

        if (flowEventPayload is StartFlow && checkpoint != null && !isInRetryState) {
            log.debug { "Ignoring duplicate '${StartFlow::class.java}'. Checkpoint has already been initialized" }
            return StateAndEventProcessor.Response(
                state,
                listOf()
            )
        }

        // flow result timeout must be lower than the processor timeout as the processor thread will be killed by the subscription consumer
        // thread after this period and so this timeout would never be reached and given a chance to return otherwise.
        val flowTimeout = (flowConfig.getLong(PROCESSOR_TIMEOUT) * 0.75).toLong()
        val result = try {
            pipeline
                .eventPreProcessing()
                .virtualNodeFlowOperationalChecks()
                .executeFlow(flowTimeout)
                .globalPostProcessing()
                .context
        } catch (e: FlowTransientException) {
            flowEventExceptionProcessor.process(e, pipeline.context)
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

        return flowEventContextConverter.convert(
            result.copy(outputRecords = result.outputRecords + cleanupEvents)
        )
    }
}
