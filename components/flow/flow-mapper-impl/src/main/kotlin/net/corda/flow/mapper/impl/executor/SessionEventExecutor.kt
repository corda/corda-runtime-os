package net.corda.flow.mapper.impl.executor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList")
class SessionEventExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
    private val flowConfig: SmartConfig,
    private val recordFactory: RecordFactory,
    private val instant: Instant,
    private val sessionInitHelper: SessionInitHelper
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): FlowMapperResult {
        val payload = sessionEvent.payload
        val sessionInit = getInitPayload(payload)
        return if (flowMapperState == null && sessionInit != null) {
            sessionInitHelper.processSessionInit(sessionEvent, sessionInit, flowConfig)
        } else if (flowMapperState == null) {
            handleNullState()
        } else {
            processOtherSessionEvents(flowMapperState)
        }
    }

    private fun getInitPayload(payload: Any): SessionInit? {
        return when (payload) {
            is SessionInit -> payload
            is SessionData -> payload.sessionInit
            else -> null
        }
    }

    private fun handleNullState(): FlowMapperResult {
        val eventPayload = sessionEvent.payload

        return if (eventPayload !is SessionError) {
            log.warn(
                "Flow mapper received session event for session which does not exist. Session may have expired. Returning error to " +
                        "counterparty. Key: $eventKey, Event: class ${sessionEvent.payload::class.java}, $sessionEvent"
            )
            val outputRecord = recordFactory.forwardError(
                sessionEvent,
                ExceptionEnvelope(
                    "FlowMapper-SessionExpired",
                    "Tried to process session event for expired session with sessionId ${sessionEvent.sessionId}"
                ),
                instant,
                flowConfig,
                sessionEvent.messageDirection,
            )
            FlowMapperResult(null, listOf(outputRecord))
        } else {
            log.warn(
                "Flow mapper received error event from counterparty for session which does not exist. Session may have expired. " +
                        "Ignoring event. Key: $eventKey, Event: $sessionEvent"
            )
            FlowMapperResult(null, listOf())
        }
    }

    /**
     * Output the session event to the correct topic and key
     */
    private fun processOtherSessionEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val messageDirection = sessionEvent.messageDirection
        val errorMsg = "Flow mapper received error event from counterparty for session which does not exist. " +
                "Session may have expired. Key: $eventKey, Event: $sessionEvent. "

        return when (flowMapperState.status) {
            null -> {
                log.warn("FlowMapperState with null status. Key: $eventKey, Event: $sessionEvent.")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.CLOSING -> {
                //todo - CORE-15757/ CORE-16184
                if (messageDirection == MessageDirection.OUTBOUND) {
                    log.warn("Attempted to send a message but flow mapper state is in CLOSING. Session ID: ${sessionEvent.sessionId}")
                    FlowMapperResult(flowMapperState, listOf())
                } else {
                    FlowMapperResult(flowMapperState, listOf())
                }
            }
            FlowMapperStateType.OPEN -> {
                val outputTopic = recordFactory.getSessionEventOutputTopic(sessionEvent, messageDirection)
                val outputRecord = if (messageDirection == MessageDirection.OUTBOUND) {
                    recordFactory.forwardEvent(sessionEvent, instant, flowConfig, messageDirection)
                } else {
                    Record(outputTopic, flowMapperState.flowId, FlowEvent(flowMapperState.flowId, sessionEvent))
                }
                FlowMapperResult(flowMapperState, listOf(outputRecord))
            }
            FlowMapperStateType.ERROR -> {
                log.warn(errorMsg + "Ignoring event.")
                FlowMapperResult(flowMapperState, listOf())
            }
        }
    }
}
