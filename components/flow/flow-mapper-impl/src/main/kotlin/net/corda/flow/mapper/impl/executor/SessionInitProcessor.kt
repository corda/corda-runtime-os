package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

/**
 * Helper class to process session events which contain a SessionInit field/payload
 */
@Component(service = [SessionInitProcessor::class])
class SessionInitProcessor @Activate constructor(
    @Reference(service = RecordFactory::class)
    private val recordFactory: RecordFactory
) {

    /**
     * Process a [sessionEvent] and [sessionInit] payload to produce a flow mapper state
     * Should be called when mapper state is null.
     * @param sessionEvent SessionEvent whose payload is SessionData or SessionInit
     * @param sessionInit session init avro object obtained from the session event
     * @param flowConfig flow config
     * @param instant timestamp
     * @return A new flow mapper state
     */
    fun processSessionInit(
        sessionEvent: SessionEvent,
        sessionInit: SessionInit,
        flowConfig: SmartConfig,
        instant: Instant
    ): FlowMapperResult {
        CordaMetrics.Metric.FlowMapperCreationCount.builder()
            .withTag(CordaMetrics.Tag.FlowEvent, sessionInit::class.java.name)
            .build().increment()

        val (flowId, outputRecord) =
            getSessionInitOutputs(
                sessionEvent.messageDirection,
                sessionEvent,
                sessionInit,
                flowConfig,
                instant
            )

        return FlowMapperResult(
            FlowMapperState(flowId, null, FlowMapperStateType.OPEN),
            listOf(outputRecord)
        )
    }

    private fun getSessionInitOutputs(
        messageDirection: MessageDirection?,
        sessionEvent: SessionEvent,
        sessionInit: SessionInit,
        flowConfig: SmartConfig,
        instant: Instant
    ): SessionInitOutputs {
        val flowId = if (messageDirection == MessageDirection.INBOUND) {
            generateFlowId()
        } else {
            // Null out the flow ID on the source session init before generating the forward record.
            val tmpFlowId = sessionInit.flowId
            sessionInit.flowId = null
            tmpFlowId
        }
        return SessionInitOutputs(
            flowId,
            recordFactory.forwardEvent(sessionEvent, instant, flowConfig, flowId)
        )
    }

    private data class SessionInitOutputs(
        val flowId: String,
        val record: Record<*, *>
    )
}
