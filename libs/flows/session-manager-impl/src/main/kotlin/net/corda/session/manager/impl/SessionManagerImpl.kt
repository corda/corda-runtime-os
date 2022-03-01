package net.corda.session.manager.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW
import net.corda.schema.configuration.FlowConfig.SESSION_MESSAGE_RESEND_WINDOW
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.session.manager.impl.processor.helper.generateAck
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.processAcks
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component
class SessionManagerImpl : SessionManager {

    private companion object {
        val sessionEventProcessorFactory = SessionEventProcessorFactory()
    }

    override fun processMessageReceived(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant):
            SessionState {
        val updatedSessionState = sessionState?.let {
            it.lastReceivedMessageTime = instant
            processAcks(event, it)
        }

        return sessionEventProcessorFactory.createEventReceivedProcessor(key, event, updatedSessionState, instant).execute()
    }

    override fun processMessageToSend(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant):
            SessionState {
        return sessionEventProcessorFactory.createEventToSendProcessor(key, event, sessionState, instant).execute()
    }

    override fun getNextReceivedEvent(sessionState: SessionState): SessionEvent? {
        val receivedEvents = sessionState.receivedEventsState ?: return null
        val undeliveredMessages = receivedEvents.undeliveredMessages
        val status = sessionState.status
        val incorrectSessionState = status == SessionStateType.CREATED || status == SessionStateType.ERROR
        return when {
            //must be an active session
            undeliveredMessages.isEmpty() -> null
            //don't allow data messages to be consumed when session is not fully established or if there is an error
            incorrectSessionState -> null
            //only allow client to see a close message after the session is closed on both sides
            status != SessionStateType.CLOSED && undeliveredMessages.first().payload is SessionClose -> null
            //return the next valid message
            undeliveredMessages.first().sequenceNum <= receivedEvents.lastProcessedSequenceNum -> undeliveredMessages.first()
            else -> null
        }
    }

    override fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState {
        return sessionState.apply {
            val receivedEvent = receivedEventsState.undeliveredMessages.find {
                it.sequenceNum == seqNum
            }
            receivedEventsState.undeliveredMessages = receivedEventsState.undeliveredMessages.minus(receivedEvent)
        }
    }

    override fun getMessagesToSend(sessionState: SessionState, instant: Instant, config: SmartConfig): Pair<SessionState,
            List<SessionEvent>> {
        val messageResendWindow = config.getLong(SESSION_MESSAGE_RESEND_WINDOW)
        val messagesToReturn = getMessagesToSend(sessionState, instant)

        //remove SessionAcks/SessionErrors and increase timestamp of messages to be sent that are awaiting acknowledgement
        clearAcksErrorsAndIncreaseTimestamps(sessionState, instant, messageResendWindow)

        //add heartbeat if no messages to send, error session if no heartbeat received within timeout
        handleHeartbeat(sessionState, config, instant, messagesToReturn)

        if (messagesToReturn.isNotEmpty()) {
            sessionState.lastSentMessageTime = instant
        }

        return Pair(sessionState, messagesToReturn)
    }

    /**
     * If no heartbeat received from counterparty after session timeout has been reached, error the session
     * If no messages to send, and current time has surpassed the heartbeat window (message resend window), send a new heartbeat.
     * @param sessionState to update
     * @param config contains message resend window and heartbeat timeout values
     * @param instant for timestamps of new messages
     * @param messagesToReturn add any new messages to send to this list
     */
    private fun handleHeartbeat(
        sessionState: SessionState,
        config: SmartConfig,
        instant: Instant,
        messagesToReturn: MutableList<SessionEvent>,
    ) {
        val messageResendWindow = config.getLong(SESSION_MESSAGE_RESEND_WINDOW)
        val lastReceivedMessageTime = sessionState.lastReceivedMessageTime

        val sessionTimeoutTimestamp = lastReceivedMessageTime.plusMillis(config.getLong(SESSION_HEARTBEAT_TIMEOUT_WINDOW))
        val scheduledHeartbeatTimestamp = sessionState.lastSentMessageTime.plusMillis(messageResendWindow)

        if (instant > sessionTimeoutTimestamp) {
            //send an error if the session has timed out
            sessionState.status = SessionStateType.ERROR
            messagesToReturn.add(
                generateErrorEvent(
                    sessionState,
                    "Session has timed out. No messages received since $lastReceivedMessageTime",
                    "SessionTimeout-Heartbeat",
                    instant
                )
            )
        } else if (messagesToReturn.isEmpty() && instant > scheduledHeartbeatTimestamp) {
            messagesToReturn.add(generateAck(sessionState, instant))
        }
    }

    /**
     * Get any new messages to send from the sendEvents state within [sessionState].
     * Send any Acks or Errors regardless of timestamps.
     * Send any other messages with a timestamp less than that of [instantInMillis].
     * Don't send a SessionAck if other events present in the send list as ack info is present at SessionEvent level on all events.
     * @param sessionState to examine sendEventsState.undeliveredMessages
     * @param instant to compare against messages to avoid resending messages in quick succession
     * @return Messages to send
     */
    private fun getMessagesToSend(
        sessionState: SessionState,
        instant: Instant
    ) : MutableList<SessionEvent> {
        //get all events with a timestamp in the past, as well as any acks or errors
        val sessionEvents = sessionState.sendEventsState.undeliveredMessages.filter {
            it.timestamp <= instant || it
                .payload is SessionAck || it.payload is SessionError
        }.toMutableList()

        //If list contains SessionAcks and non SessionAcks, remove the SessionAcks as ack info is already at SessionEvent level
        if (sessionEvents.any { it.payload is SessionAck} && sessionEvents.any { it.payload !is SessionAck }) {
            sessionEvents.removeIf { it.payload is SessionAck }
        }

        //update events with the latest ack info from the current state
        sessionEvents.forEach { sessionEvent ->
            sessionEvent.receivedSequenceNum = sessionState.receivedEventsState.lastProcessedSequenceNum
            sessionEvent.outOfOrderSequenceNums = sessionState.receivedEventsState.undeliveredMessages.map { it.sequenceNum }
        }

        return sessionEvents
    }

    /**
     * Remove Acks and Errors from the session state sendEvents.undeliveredMessages as these cannot be acked by a counterparty.
     * Increase the timestamps of messages with a timestamp less than [instantInMillis]
     * by the configurable value of the [messageResendWindow]. This will avoid message resends in quick succession.
     * @param sessionState to update the sendEventsState.undeliveredMessages
     * @param instant to update the sendEventsState.undeliveredMessages
     */
    private fun clearAcksErrorsAndIncreaseTimestamps(
        sessionState: SessionState,
        instant: Instant,
        messageResendWindow: Long
    ) {
        sessionState.sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.filter {
            it.payload !is SessionAck && it.payload !is SessionError
        }.map {
            if (it.timestamp <= instant) {
                it.timestamp = instant.plusMillis(messageResendWindow)
            }
            it
        }
    }
}
