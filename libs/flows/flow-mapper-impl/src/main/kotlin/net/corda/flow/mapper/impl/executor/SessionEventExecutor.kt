package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.v5.base.util.contextLogger

class SessionEventExecutor(
    private val eventKey: String,
    private val messageDirection: MessageDirection,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    private val flowKeyGenerator = FlowKeyGenerator()
    private val outputTopic = getSessionEventOutputTopic(messageDirection)

    override fun execute(): FlowMapperResult {
        return when (val sessionEventPayload = sessionEvent.payload) {
            is SessionInit -> {
                if (flowMapperState == null) {
                    processSessionInit(sessionEvent, sessionEventPayload)
                } else {
                    //duplicate
                    log.warn(
                        "Duplicate SessionInit event received. Key: $eventKey, Event: $sessionEvent"
                    )
                    FlowMapperResult(flowMapperState, emptyList())
                }
            }
            else -> {
                if (flowMapperState == null) {
                    //expired closed session. This is likely a bug and we should return an error event to the sender - CORE-3207
                    log.error("Event received for expired closed session. Key: $eventKey, Event: $sessionEvent")
                    FlowMapperResult(flowMapperState, emptyList())
                } else {
                    processOtherSessionEvents(flowMapperState)
                }
            }
        }
    }

    private fun processOtherSessionEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val (recordKey, recordValue) = if (messageDirection == MessageDirection.OUTBOUND) {
            Pair(generateOutboundSessionId(eventKey), FlowMapperEvent(messageDirection, sessionEvent))
        } else {
            Pair(flowMapperState.flowKey, FlowEvent(flowMapperState.flowKey, sessionEvent))
        }

        return FlowMapperResult(
            flowMapperState,
            listOf(
                Record(
                    outputTopic,
                    recordKey,
                    recordValue
                )
            )
        )
    }

    private fun processSessionInit(sessionEvent: SessionEvent, sessionInit: SessionInit): FlowMapperResult {
        val identity  = if (messageDirection == MessageDirection.OUTBOUND) {
            sessionInit.flowKey.identity
        } else {
            sessionInit.initiatedIdentity
        }


        val (flowKey, outputRecordKey, outputRecordValue) =
            getSessionInitOutputs(
                messageDirection,
                eventKey,
                sessionEvent,
                sessionInit,
                identity
            )

        return FlowMapperResult(
            FlowMapperState(flowKey, null, FlowMapperStateType.OPEN),
            listOf(Record(outputTopic, outputRecordKey, outputRecordValue))
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
        sessionKey: String,
        sessionEvent: SessionEvent,
        sessionInit: SessionInit,
        ourIdentity: HoldingIdentity
    ): SessionInitOutputs {
        return if (messageDirection == MessageDirection.INBOUND) {
            val flowKey = flowKeyGenerator.generateFlowKey(ourIdentity)
            sessionInit.flowKey = flowKey
            sessionEvent.payload = sessionInit
            SessionInitOutputs(flowKey, flowKey, FlowEvent(flowKey, sessionEvent))
        } else {
            //reusing SessionInit object for inbound and outbound traffic rather than creating a new object identical to SessionInit
            //with an extra field of flowKey. set flowkey to null to not expose it on outbound messages
            val tmpFLowEventKey = sessionInit.flowKey
            sessionInit.flowKey = null
            sessionEvent.payload = sessionInit

            SessionInitOutputs(
                tmpFLowEventKey,
                generateOutboundSessionId(sessionKey),
                FlowMapperEvent(MessageDirection.OUTBOUND, sessionEvent)
            )
        }
    }

    private fun generateOutboundSessionId(sessionKey: String): String {
        return if (sessionKey.endsWith("-INITIATED")) {
            sessionKey.removeSuffix("-INITIATED")
        } else {
            "$sessionKey-INITIATED"
        }
    }

    /**
     * Get the output topic based on [messageDirection].
     * Inbound records should be directed to the flow event topic.
     * Outbound records should be directed to the p2p out topic.
     */
    private fun getSessionEventOutputTopic(messageDirection: MessageDirection): String {
        return if (messageDirection == MessageDirection.INBOUND) {
            FLOW_EVENT_TOPIC
        } else {
            P2P_OUT_TOPIC
        }
    }

    data class SessionInitOutputs(
        val flowKey: FlowKey,
        val outputRecordKey: Any,
        val outputRecordValue: Any
    )
}
