package net.corda.session.manager.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component
class SessionManagerImpl : SessionManager {

    private val sessionEventProcessorFactory = SessionEventProcessorFactory()

    override fun processMessageReceived(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant):
            SessionEventResult {
        return sessionEventProcessorFactory.createEventReceivedProcessor(key, event, sessionState, instant).execute()
    }

    override fun processMessageToSend(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant):
            SessionEventResult {
        return sessionEventProcessorFactory.createEventToSendProcessor(key, event, sessionState, instant).execute()
    }

    override fun getNextReceivedEvent(sessionState: SessionState): SessionEvent? {
        val receivedEvents = sessionState.receivedEventsState ?: return null
        val undeliveredMessages = receivedEvents.undeliveredMessages
        return when {
            undeliveredMessages.isEmpty() -> null
            undeliveredMessages.first().sequenceNum <= receivedEvents.lastProcessedSequenceNum -> undeliveredMessages.first()
            else -> null
        }
    }

    override fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState {
        val undeliveredEventsReceived = sessionState.receivedEventsState.undeliveredMessages.toMutableList()
        undeliveredEventsReceived.removeIf { it.sequenceNum == seqNum }
        sessionState.receivedEventsState.undeliveredMessages = undeliveredEventsReceived
        return sessionState
    }
}
