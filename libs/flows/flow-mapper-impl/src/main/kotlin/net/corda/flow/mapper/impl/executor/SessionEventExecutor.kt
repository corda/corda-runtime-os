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
import net.corda.flow.mapper.FlowKeyGenerator
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast

class SessionEventExecutor(
    private val flowMapperMetaData: FlowMapperMetaData
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    private val flowKeyGenerator = FlowKeyGenerator()

    override fun execute(): FlowMapperResult {
        val flowMapperState = flowMapperMetaData.flowMapperState
        val sessionEvent: SessionEvent = uncheckedCast(flowMapperMetaData.payload)
        return when (val sessionEventPayload = sessionEvent.payload) {
            is SessionInit -> {
                if (flowMapperState == null) {
                    processSessionInit(sessionEvent, sessionEventPayload)
                } else {
                    //duplicate
                    log.warn(
                        "Duplicate SessionInit event received. Key: ${flowMapperMetaData.flowMapperEventKey}, Event: " +
                                "${flowMapperMetaData.payload}"
                    )
                    FlowMapperResult(flowMapperState, emptyList())
                }
            }
            else -> {
                if (flowMapperState == null) {
                    //expired closed session
                    log.warn(
                        "Event received for expired closed session. Key: ${flowMapperMetaData.flowMapperEventKey}, " +
                                "Event: ${flowMapperMetaData.payload}"
                    )
                    FlowMapperResult(flowMapperState, emptyList())
                } else {
                    processOtherSessionEvents(flowMapperState)
                }
            }
        }
    }

    private fun processOtherSessionEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val outputTopic = flowMapperMetaData.outputTopic ?: throw CordaRuntimeException(
            "Output topic should not be null for " +
                    "SessionEvent on key ${flowMapperMetaData.flowMapperEventKey}"
        )
        val (recordKey, recordValue) = if (flowMapperMetaData.messageDirection == MessageDirection.OUTBOUND) {
            Pair(generateOutboundSessionId(flowMapperMetaData.flowMapperEventKey), flowMapperMetaData.flowMapperEvent)
        } else {
            Pair(flowMapperState.flowKey, FlowEvent(flowMapperState.flowKey, flowMapperMetaData.payload))
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
        val identity = flowMapperMetaData.holdingIdentity
            ?: throw CordaRuntimeException("Holding identity not set for SessionInit on key ${flowMapperMetaData.flowMapperEventKey}")
        val outputTopic = flowMapperMetaData.outputTopic ?: throw CordaRuntimeException(
            "Output topic should not be null for SessionInit on key ${flowMapperMetaData.flowMapperEventKey}"
        )

        val (flowKey, outputRecordKey, outputRecordValue) =
            getSessionInitOutputs(
                flowMapperMetaData.messageDirection,
                flowMapperMetaData.flowMapperEventKey,
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

    data class SessionInitOutputs(
        val flowKey: FlowKey,
        val outputRecordKey: Any,
        val outputRecordValue: Any
    )
}
