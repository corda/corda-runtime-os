package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.FlowEventProcessor
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class FlowEventProcessorImpl(
    private val flowEventPipelineFactory: FlowEventPipelineFactory,
    private val flowEventExceptionProcessor: FlowEventExceptionProcessor,
    private val flowEventContextConverter: FlowEventContextConverter,
    private val config: SmartConfig
) : FlowEventProcessor {

    private companion object {
        val log = contextLogger()
    }

    override val keyClass = String::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java

    init{
        // This works for now, but we should consider introducing a provider we could then inject it into
        // the classes that need it rather than passing it through all the layers.
        flowEventExceptionProcessor.configure(config)
    }

    override fun onNext(
        state: Checkpoint?,
        event: Record<String, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value

        if (flowEvent == null) {
            log.error("The incoming event record '${event}' contained a null FlowEvent, this event will be discarded")
            return StateAndEventProcessor.Response(state, listOf())
        }

        log.info("Flow [${event.key}] Received event: ${flowEvent.payload::class.java} / ${flowEvent.payload}")

        return try {
            val pipeline = flowEventPipelineFactory.create(state, flowEvent, config)

            flowEventContextConverter.convert(pipeline
                .eventPreProcessing()
                .runOrContinue()
                .setCheckpointSuspendedOn()
                .setWaitingFor()
                .requestPostProcessing()
                .globalPostProcessing()
                .context
            )

        } catch (e: FlowTransientException) {
            flowEventExceptionProcessor.process(e)
        } catch (e: FlowEventException) {
            flowEventExceptionProcessor.process(e)
        } catch (e: FlowFatalException) {
            flowEventExceptionProcessor.process(e)
        } catch (e: Exception) {
            flowEventExceptionProcessor.process(e)
        }
    }
}


