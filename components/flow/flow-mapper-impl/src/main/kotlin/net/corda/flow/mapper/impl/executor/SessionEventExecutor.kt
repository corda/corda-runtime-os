package net.corda.flow.mapper.impl.executor

import net.corda.data.ExceptionEnvelope
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
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.time.Instant

@Suppress("LongParameterList")
class SessionEventExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
    private val flowConfig: SmartConfig,
    private val recordFactory: RecordFactory,
    private val instant: Instant,
    private val sessionInitProcessor: SessionInitProcessor
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute() = if (flowMapperState == null) {
        getInitPayload(sessionEvent.payload)?.let { sessionInit->
            sessionInitProcessor.processSessionInit(sessionEvent, sessionInit, flowConfig, instant)
        } ?: handleNullState()
    } else {
        processOtherSessionEvents(flowMapperState)
    }

    private fun getInitPayload(payload: Any) = when (payload) {
        is SessionInit -> payload
        is SessionData -> payload.sessionInit
        else -> null
    }

    private fun handleNullState(): FlowMapperResult {
        val eventPayload = sessionEvent.payload

        return if (eventPayload !is SessionError) {
            log.warn(
                "Flow mapper received session event for session which does not exist. Session may have expired. Returning error to " +
                        "counterparty. Key: $eventKey, Event: class ${sessionEvent.payload::class.java}, $sessionEvent"
            )
            // In this case, the error message should not be forwarded through the mapper, and instead should be sent
            // back from where it came. Note that at present if the flow engine sends a data message without first
            // sending an init message this will result in failure, as the mapper has no knowledge of the flow ID to
            // respond on.
            val outputRecords = try {
                val record = recordFactory.sendBackError(
                    sessionEvent,
                    ExceptionEnvelope(
                        "FlowMapper-SessionExpired",
                        "Tried to process session event for expired session with sessionId ${sessionEvent.sessionId}"
                    ),
                    instant,
                    flowConfig
                )
                listOf(record)
            } catch (e: IllegalArgumentException) {
                log.warn("Flow mapper received an outbound session message for session ${sessionEvent.sessionId} where " +
                        "the session does not exist. Discarding the message.")
                listOf()
            }
            FlowMapperResult(null, outputRecords)
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
        return when (flowMapperState.status) {
            null -> {
                log.warn("FlowMapperState with null status. Key: $eventKey, Event: $sessionEvent.")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.CLOSING, FlowMapperStateType.ERROR -> {
                log.warn("Attempted to process a message ${sessionEvent.messageDirection} but flow mapper state is " +
                        "in ${flowMapperState.status}. Session ID: ${sessionEvent.sessionId}. Ignoring Event")
                FlowMapperResult(flowMapperState, listOf())
            }
            FlowMapperStateType.OPEN -> {
                val outputRecord = recordFactory.forwardEvent(sessionEvent, instant, flowConfig, flowMapperState.flowId)
                FlowMapperResult(flowMapperState, listOf(outputRecord))
            }
        }
    }
}
