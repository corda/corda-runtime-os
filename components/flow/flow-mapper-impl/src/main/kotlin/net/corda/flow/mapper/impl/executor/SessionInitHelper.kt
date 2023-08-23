package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.FlowEvent
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
import net.corda.schema.Schemas
import java.time.Instant

/**
 * Helper class to process session events which contain a SessionInit field/payload
 */
class SessionInitHelper(private val recordFactory: RecordFactory) {

    /**
     * Process a [sessionEvent] and [sessionInit] payload to producer a flow mapper state
     * Should be called when mapper state is null.
     * @param sessionEvent SessionEvent whose payload is SessionData or SessionInit
     * @param sessionInit session init avro object obtained from the session event
     * @param flowConfig flow config
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

        val (flowKey, outputRecord) =
            getSessionInitOutputs(
                sessionEvent.messageDirection,
                sessionEvent,
                sessionInit,
                flowConfig,
                instant
            )

        return FlowMapperResult(
            FlowMapperState(flowKey, null, FlowMapperStateType.OPEN),
            listOf(outputRecord)
        )
    }

    /**
     * Get a helper object to obtain:
     * - flow key
     * - output record key
     * - output record value
     */
    private fun getSessionInitOutputs(
        messageDirection: MessageDirection?,
        sessionEvent: SessionEvent,
        sessionInit: SessionInit,
        flowConfig: SmartConfig,
        instant: Instant
    ): SessionInitOutputs {
        return if (messageDirection == MessageDirection.INBOUND) {
            val flowId = generateFlowId()
            sessionInit.flowId = flowId
            SessionInitOutputs(
                flowId,
                Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowId, FlowEvent(flowId, sessionEvent))
            )
        } else {
            //reusing SessionInit object for inbound and outbound traffic rather than creating a new object identical to SessionInit
            //with an extra field of flowKey. set flowkey to null to not expose it on outbound messages
            val tmpFLowEventKey = sessionInit.flowId
            sessionInit.flowId = null
            sessionEvent.payload = sessionInit

            SessionInitOutputs(
                tmpFLowEventKey,
                recordFactory.forwardEvent(sessionEvent, instant, flowConfig, sessionEvent.messageDirection)
            )
        }
    }

    data class SessionInitOutputs(
        val flowId: String,
        val record: Record<*, *>
    )
}