package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.utilities.withMDC
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace

class FlowEventProcessorImpl(
    private val flowEventPipelineFactory: FlowEventPipelineFactory,
    private val flowEventExceptionProcessor: FlowEventExceptionProcessor,
    private val flowEventContextConverter: FlowEventContextConverter,
    private val config: SmartConfig
) : StateAndEventProcessor<String, Checkpoint, FlowEvent> {

    private companion object {
        val log = contextLogger()
        const val MDC_CLIENT_ID = "client_id"
        const val MDC_VNODE_ID = "vnode_id"
        const val MDC_EXTERNAL_EVENT_ID = "external_event_id"
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
        event: Record<String, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val flowEvent = event.value
        val mdcProperties = getFlowMDCLogging(state, flowEvent)
        return withMDC(mdcProperties) {
            getFlowPipelineResponse(flowEvent, event, state, mdcProperties)
        }
    }

    private fun getFlowPipelineResponse(
        flowEvent: FlowEvent?,
        event: Record<String, FlowEvent>,
        state: Checkpoint?,
        mdcProperties: Map<String, String>
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
        } catch (t: Throwable) {
            flowEventExceptionProcessor.process(t)
        }
    }

    /**
     * Extract out the MDC logging info from a flow event and checkpoint.
     * @param checkpoint the checkpoint for a flow. can be null if it is the first flow event for this key.
     * @param event the flow event received. MDC info can be extracted from the [StartFlow] event when the checkpoint is null.
     * @return Map of fields to populate within the MDC taken from the flow.
     */
    private fun getFlowMDCLogging(checkpoint: Checkpoint?, event: FlowEvent?): Map<String, String> {
        return if (checkpoint != null) {
            getMDCFromCheckpoint(checkpoint)
        } else {
            getMDCFromEvent(event)
        }
    }

    /**
     * Extract out the MDC logging info from a [flowEvent]. The flow event is expected to be of type [StartFlow] or a [SessionInit].
     */
    private fun getMDCFromEvent(flowEvent: FlowEvent?): Map<String, String> {
        val payload = flowEvent?.payload
        return if (payload is StartFlow) {
            val startContext = payload.startContext
            val startKey = startContext.statusKey
            val holdingIdentityShortHash = startKey.identity.toCorda().shortHash.toString()
            return mapOf(MDC_VNODE_ID to holdingIdentityShortHash, MDC_CLIENT_ID to startContext.requestId)
        } else if (payload is SessionEvent && payload.payload is SessionInit){
            val holdingIdentityShortHash = payload.initiatedIdentity.toCorda().shortHash.toString()
            return mapOf(MDC_VNODE_ID to holdingIdentityShortHash, MDC_CLIENT_ID to payload.sessionId)
        } else {
            //this shouldn't happen
            val payloadType = if (payload != null) payload::class else null
            log.warn("Failed to set MDC. Flow event with null state where event payload is of type $payloadType")
            emptyMap()
        }
    }

    /**
     * Extract out the MDC logging info from the [checkpoint].
     */
    private fun getMDCFromCheckpoint(state: Checkpoint): Map<String, String> {
        val flowState = state.flowState ?: return emptyMap()
        val startContext = flowState.flowStartContext
        val vNodeShortHash = startContext.identity.toCorda().shortHash.toString()
        val mdcLogging = mutableMapOf(MDC_VNODE_ID to vNodeShortHash, MDC_CLIENT_ID to startContext.requestId)
        setExternalEventIdIfNotComplete(flowState, mdcLogging)
        return mdcLogging
    }

    /**
     * If a response has not been received from the external event or if it is still retrying a request then set the external event id
     * into the MDC.
     */
    private fun setExternalEventIdIfNotComplete(
        flowState: FlowState,
        mdcLogging: MutableMap<String, String>
    ) {
        val extState = flowState.externalEventState
        if (extState != null) {
            val status = extState.status
            if (extState.response == null || status.type == ExternalEventStateType.RETRY) {
                mdcLogging[MDC_EXTERNAL_EVENT_ID] = extState.requestId
            }
        }
    }
}
