package net.corda.session.manager.impl

import net.corda.data.flow.event.MessageDirection
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
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
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
        var messagesToReturn = getMessagesToSendAndUpdateSendState(sessionState, instant, config)

        //add heartbeat if no messages sent recently, add ack if needs to be sent, error session if no heartbeat received within timeout
        messagesToReturn = handleHeartbeatAndAcknowledgements(sessionState, config, instant, messagesToReturn)

        if (messagesToReturn.isNotEmpty()) {
            sessionState.sendAck = false
            sessionState.lastSentMessageTime = instant
        }

        return Pair(sessionState, messagesToReturn)
    }

    /**
     * If no heartbeat received from counterparty after session timeout has been reached, error the session
     * If no messages to send, and current time has surpassed the heartbeat window (message resend window), send a new ack.
     * If no messages to send, and there are messages received to be acked, send an ack.
     * else return back [messagesToReturn] unedited
     * @param sessionState to update
     * @param config contains message resend window and heartbeat timeout values
     * @param instant for timestamps of new messages
     * @param messagesToReturn add any new messages to send to this list
     * @return Messages to send to the counterparty
     */
    private fun handleHeartbeatAndAcknowledgements(
        sessionState: SessionState,
        config: SmartConfig,
        instant: Instant,
        messagesToReturn: List<SessionEvent>,
    ): List<SessionEvent> {
        val messageResendWindow = config.getLong(SESSION_MESSAGE_RESEND_WINDOW)
        val lastReceivedMessageTime = sessionState.lastReceivedMessageTime

        val sessionTimeoutTimestamp = lastReceivedMessageTime.plusMillis(config.getLong(SESSION_HEARTBEAT_TIMEOUT_WINDOW))
        val scheduledHeartbeatTimestamp = sessionState.lastSentMessageTime.plusMillis(messageResendWindow)

        return if (instant > sessionTimeoutTimestamp) {
            //send an error if the session has timed out
            sessionState.status = SessionStateType.ERROR
            listOf(generateErrorEvent(
                sessionState,
                "Session has timed out. No messages received since $lastReceivedMessageTime",
                "SessionTimeout-Heartbeat",
                instant
            ))

        } else if (messagesToReturn.isEmpty() && (instant > scheduledHeartbeatTimestamp || sessionState.sendAck)) {
            listOf(generateAck(sessionState, instant))
        } else {
            messagesToReturn
        }
    }

    /**
     * Get any new messages to send from the sendEvents state within [sessionState].
     * Send any Acks or Errors regardless of timestamps.
     * Send any other messages with a timestamp less than that of [instantInMillis].
     * Don't send a SessionAck if other events present in the send list as ack info is present at SessionEvent level on all events.
     * Update sendEvents state to remove acks/errors and increase timestamps of messages to send.
     * @param sessionState to examine sendEventsState.undeliveredMessages
     * @param instant to compare against messages to avoid resending messages in quick succession
     * @return Messages to send
     */
    private fun getMessagesToSendAndUpdateSendState(
        sessionState: SessionState,
        instant: Instant,
        config: SmartConfig
    ): List<SessionEvent> {
        //get all events with a timestamp in the past, as well as any acks or errors
        val sessionEvents = sessionState.sendEventsState.undeliveredMessages.filter {
            it.timestamp <= instant || it.payload is SessionError
        }

        //update events with the latest ack info from the current state
        sessionEvents.forEach { eventToSend ->
            eventToSend.receivedSequenceNum = sessionState.receivedEventsState.lastProcessedSequenceNum
            eventToSend.outOfOrderSequenceNums = sessionState.receivedEventsState.undeliveredMessages.map { it.sequenceNum }
        }

        //remove SessionAcks/SessionErrors and increase timestamp of messages to be sent that are awaiting acknowledgement
        val messageResendWindow = config.getLong(SESSION_MESSAGE_RESEND_WINDOW)
        updateSessionStateSendEvents(sessionState, instant, messageResendWindow)

        return sessionEvents
    }

    /**
     * Remove Acks and Errors from the session state sendEvents.undeliveredMessages as these cannot be acked by a counterparty.
     * Increase the timestamps of messages with a timestamp less than [instantInMillis]
     * by the configurable value of the [messageResendWindow]. This will avoid message resends in quick succession.
     * @param sessionState to update the sendEventsState.undeliveredMessages
     * @param instant to update the sendEventsState.undeliveredMessages
     */
    private fun updateSessionStateSendEvents(
        sessionState: SessionState,
        instant: Instant,
        messageResendWindow: Long
    ) {
        sessionState.sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.filter {
            it.payload !is SessionError
        }.map {
            if (it.timestamp <= instant) {
                it.timestamp = instant.plusMillis(messageResendWindow)
            }
            it
        }
    }

    /**
     * Remove any messages from the send events state that have been acknowledged by the counterparty.
     * Examine the [sessionEvent] to get the highest contiguous sequence number received by the other side
     * as well as any out of order messages
     * they have also received. Remove these events if present from the sendEvents undelivered messages.
     * If the current session state has a status of WAIT_FOR_FINAL_ACK and the ack info contains the sequence number of the session close
     * message then the session can be set to CLOSED.
     * If the current session state has a status of CREATED and the SessionInit has been acked then the session can be set to CONFIRMED
     *
     * @param sessionEvent to get ack info from
     * @param sessionState to get the sent events
     * @return Session state updated with any messages that were delivered to the counterparty removed from [sessionState].sendEventsState
     */
    private fun processAcks(sessionEvent: SessionEvent, sessionState: SessionState): SessionState {
        val highestContiguousSeqNum = sessionEvent.receivedSequenceNum
        val outOfOrderSeqNums = sessionEvent.outOfOrderSequenceNums

        val undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.filter {
            it.sequenceNum == null ||
                    (it.sequenceNum > highestContiguousSeqNum &&
                            (outOfOrderSeqNums.isNullOrEmpty() || !outOfOrderSeqNums.contains(it.sequenceNum)))
        }

        return sessionState.apply {
            sendEventsState.undeliveredMessages = undeliveredMessages
            val nonAckUndeliveredMessages = undeliveredMessages.filter { it.payload !is SessionAck }
            if (status == SessionStateType.WAIT_FOR_FINAL_ACK && nonAckUndeliveredMessages.isEmpty()) {
                status = SessionStateType.CLOSED
            } else if (status == SessionStateType.CREATED && nonAckUndeliveredMessages.isEmpty()) {
                status = SessionStateType.CONFIRMED
            }
        }
    }

    /**
     * Generate an SessionAck containing the latest info regarding messages received.
     * @param sessionState to examine which messages have been received
     * @param instant to set timestamp on SessionAck
     * @return A SessionAck SessionEvent with ack fields set on the SessionEvent based on messages received from a counterparty
     */
    private fun generateAck(sessionState: SessionState, instant: Instant): SessionEvent {
        val receivedEventsState = sessionState.receivedEventsState
        val outOfOrderSeqNums = receivedEventsState.undeliveredMessages.map { it.sequenceNum }
        return SessionEvent.newBuilder()
            .setMessageDirection(MessageDirection.OUTBOUND)
            .setTimestamp(instant)
            .setSequenceNum(null)
            .setSessionId(sessionState.sessionId)
            .setReceivedSequenceNum(receivedEventsState.lastProcessedSequenceNum)
            .setOutOfOrderSequenceNums(outOfOrderSeqNums)
            .setPayload(SessionAck())
            .build()
    }
}
