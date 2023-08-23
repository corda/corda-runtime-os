package net.corda.flow.mapper.impl.executor

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record

/**
 * Helper class to process session events which contain a SessionInit field/payload
 */
class SessionInitHelper(private val sessionEventSerializer: CordaAvroSerializer<SessionEvent>) {

    /**
     * Process a [sessionEvent] and [sessionInit] payload to producer a flow mapper state
     * Should be called when mapper state is null.
     * @param sessionEvent SessionEvent whose payload is SessionData or SessionInit
     * @param sessionInit session init avro object obtained from the session event
     * @param flowConfig flow config
     * @return A new flow mapper state
     */
    fun processSessionInit(sessionEvent: SessionEvent, sessionInit: SessionInit, flowConfig: SmartConfig): FlowMapperResult {
        val messageDirection = sessionEvent.messageDirection
        val outputTopic = getSessionEventOutputTopic(messageDirection)
        val (flowId, outputRecordKey, outputRecordValue) =
            getSessionInitOutputs(
                messageDirection,
                sessionEvent,
                sessionInit,
                flowConfig
            )

        return FlowMapperResult(
            FlowMapperState(flowId, null, FlowMapperStateType.OPEN),
            listOf(Record(outputTopic, outputRecordKey, outputRecordValue))
        )
    }

    private fun getSessionInitOutputs(
        messageDirection: MessageDirection?,
        sessionEvent: SessionEvent,
        sessionInit: SessionInit,
        flowConfig: SmartConfig,
    ): SessionInitOutputs {
        return if (messageDirection == MessageDirection.INBOUND) {
            val flowId = generateFlowId()
            sessionInit.flowId = flowId
            SessionInitOutputs(flowId, flowId, FlowEvent(flowId, sessionEvent))
        } else {
            //reusing SessionInit object for inbound and outbound traffic rather than creating a new object identical to SessionInit
            //with an extra field of flowId. set flowId to null to not expose it on outbound messages
            val tmpFLowEventKey = sessionInit.flowId
            sessionInit.flowId = null

            SessionInitOutputs(
                tmpFLowEventKey,
                sessionEvent.sessionId,
                generateAppMessage(sessionEvent, sessionEventSerializer, flowConfig)
            )
        }
    }

    data class SessionInitOutputs(
        val flowId: String,
        val outputRecordKey: Any,
        val outputRecordValue: Any
    )
}