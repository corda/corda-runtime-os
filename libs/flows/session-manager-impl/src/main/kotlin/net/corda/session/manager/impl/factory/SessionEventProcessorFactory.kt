package net.corda.session.manager.impl.factory

import java.time.Instant
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.session.manager.SessionManagerException
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.SessionAckProcessorReceive
import net.corda.session.manager.impl.processor.SessionCloseProcessorReceive
import net.corda.session.manager.impl.processor.SessionCloseProcessorSend
import net.corda.session.manager.impl.processor.SessionDataProcessorReceive
import net.corda.session.manager.impl.processor.SessionDataProcessorSend
import net.corda.session.manager.impl.processor.SessionErrorProcessorReceive
import net.corda.session.manager.impl.processor.SessionErrorProcessorSend
import net.corda.session.manager.impl.processor.SessionInitProcessorReceive
import net.corda.session.manager.impl.processor.SessionInitProcessorSend
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [SessionEventProcessorFactory::class])
class SessionEventProcessorFactory @Activate constructor(
    @Reference(service = MessagingChunkFactory::class)
    private val messagingChunkFactory: MessagingChunkFactory,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Get the correct processor for the [sessionEvent] received.
     * [key] is provided for logging purposes
     * [sessionState] is provided for the given [sessionEvent]
     * [instant] is provided for timestamps
     */
    fun createEventReceivedProcessor(key: Any, sessionEvent: SessionEvent, sessionState: SessionState?, instant: Instant):
            SessionEventProcessor {
        val messageDirection = sessionEvent.messageDirection
        if (messageDirection != MessageDirection.INBOUND) {
            throw SessionManagerException("MessageDirection $messageDirection must be set to ${MessageDirection.INBOUND}" +
                    " for factory method createReceivedEventProcessor()")
        }

        return when (val payload = sessionEvent.payload) {
            is SessionInit -> SessionInitProcessorReceive(key, sessionState, sessionEvent, instant)
            is SessionData -> SessionDataProcessorReceive(key, sessionState, sessionEvent, instant)
            is SessionClose -> SessionCloseProcessorReceive(key, sessionState, sessionEvent, instant)
            is SessionError -> SessionErrorProcessorReceive(key, sessionState, sessionEvent, payload.errorMessage, instant)
            is SessionAck -> SessionAckProcessorReceive(key, sessionState, sessionEvent, instant)
            else -> throw NotImplementedError(
                "The session event type '${payload.javaClass.name}' is not supported."
            )
        }
    }

    /**
     * Get the correct processor for the [sessionEvent] to be sent to a counterparty.
     * [key] is provided for logging purposes
     * [sessionState] is provided for the given [sessionEvent]
     * [instant] is provided for timestamps
     */
    fun createEventToSendProcessor(
        key: Any,
        sessionEvent: SessionEvent,
        sessionState: SessionState?,
        instant: Instant,
        maxMsgSize: Long,
    ): SessionEventProcessor {
        val messageDirection = sessionEvent.messageDirection

        if (messageDirection != MessageDirection.OUTBOUND) {
            throw SessionManagerException("MessageDirection $messageDirection must be set to ${MessageDirection.OUTBOUND} " +
                    "for factory method createEventToSendProcessor()")
        }
        return when (val payload = sessionEvent.payload) {
            is SessionInit -> SessionInitProcessorSend(key, sessionState, sessionEvent, instant)
            is SessionData -> {
                val chunkSerializer = messagingChunkFactory.createChunkSerializerService(maxMsgSize)
                SessionDataProcessorSend(key, sessionState, sessionEvent, instant, chunkSerializer, payload)
            }
            is SessionClose -> SessionCloseProcessorSend(key, sessionState, sessionEvent, instant)
            is SessionError -> SessionErrorProcessorSend(key, sessionState, sessionEvent, payload.errorMessage, instant)
            else -> throw NotImplementedError(
                "The session event type '${payload.javaClass.name}' is not supported."
            )
        }
    }
}
