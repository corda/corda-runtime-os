package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
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
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.utilities.withMDC
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

class FlowEventProcessorImpl(
    private val flowEventPipelineFactory: FlowEventPipelineFactory,
    private val flowEventExceptionProcessor: FlowEventExceptionProcessor,
    private val flowEventContextConverter: FlowEventContextConverter,
    private val config: SmartConfig,
    private val flowMDCService: FlowMDCService,
) : StateAndEventProcessor<String, Checkpoint, FlowEvent> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass = String::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java

    init {
        // This works for now, but we should consider introducing a provider we could then inject it into
        // the classes that need it rather than passing it through all the layers.
        flowEventExceptionProcessor.configure(config)
    }

    override fun onNext(
        state: Checkpoint?,
        event: Record<String, FlowEvent>,
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value
        val mdcProperties = flowMDCService.getMDCLogging(state, flowEvent, event.key)
        return withMDC(mdcProperties) {
            getFlowPipelineResponse(flowEvent, event, state, mdcProperties)
        }
    }

    private fun getFlowPipelineResponse(
        flowEvent: FlowEvent?,
        event: Record<String, FlowEvent>,
        state: Checkpoint?,
        mdcProperties: Map<String, String>,
    ): StateAndEventProcessor.Response<Checkpoint> {
        if (flowEvent == null) {
            log.debug { "The incoming event record '${event}' contained a null FlowEvent, this event will be discarded" }
            return StateAndEventProcessor.Response(state, listOf())
        }

        val pipeline = try {
            log.trace { "Flow [${event.key}] Received event: ${flowEvent.payload::class.java} / ${flowEvent.payload}" }
            flowEventPipelineFactory.create(state, flowEvent, config, mdcProperties)
        } catch (t: Throwable) {
            // Without a pipeline there's a limit to what can be processed.
            return flowEventExceptionProcessor.process(t)
        }

        // flow result timeout must be lower than the processor timeout as the processor thread will be killed by the subscription consumer
        // thread after this period and so this timeout would never be reached and given a chance to return otherwise.
        val flowTimeout = (config.getLong(PROCESSOR_TIMEOUT) * 0.75).toLong()
        return try {
            flowEventContextConverter.convert(
                pipeline
                    .eventPreProcessing()
                    .virtualNodeFlowOperationalChecks()
                    .runOrContinue(flowTimeout)
                    .setCheckpointSuspendedOn()
                    .setWaitingFor()
                    .requestPostProcessing()
                    .globalPostProcessing()
                    .context
            )
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
            flowEventExceptionProcessor.process(t)
        }
    }
}
