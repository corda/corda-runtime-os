package net.corda.p2p.linkmanager.inbound

import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAck
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.DataMessagePayload
import net.corda.p2p.HeartbeatMessage
import net.corda.p2p.HeartbeatMessageAck
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.MessageAck
import net.corda.p2p.SessionPartitions
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.common.AvroSealedClasses
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

internal class InboundMessageProcessor(
    private val sessionManager: SessionManager,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val members: LinkManagerMembershipGroupReader,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val clock: Clock
) :
    EventLogProcessor<String, LinkInMessage> {

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
        val records = mutableListOf<Record<*, *>>()
        for (event in events) {
            val message = event.value
            if (message == null) {
                logger.error("Received null message. The message was discarded.")
                continue
            }
            records += when (val payload = message.payload) {
                is AuthenticatedDataMessage -> processDataMessage(
                    payload.header.sessionId,
                    AvroSealedClasses.DataMessage.Authenticated(payload)
                )
                is AuthenticatedEncryptedDataMessage -> processDataMessage(
                    payload.header.sessionId,
                    AvroSealedClasses.DataMessage.AuthenticatedAndEncrypted(payload)
                )
                is ResponderHelloMessage, is ResponderHandshakeMessage, is InitiatorHandshakeMessage, is InitiatorHelloMessage -> {
                    processSessionMessage(message)
                }
                is UnauthenticatedMessage -> {
                    listOf(Record(Schemas.P2P.P2P_IN_TOPIC, LinkManager.generateKey(), AppMessage(payload)))
                }
                else -> {
                    logger.error("Received unknown payload type ${message.payload::class.java.simpleName}. The message was discarded.")
                    emptyList()
                }
            }
        }
        return records
    }

    private fun processSessionMessage(message: LinkInMessage): List<Record<String, *>> {
        val response = sessionManager.processSessionMessage(message)
        return if (response != null) {
            when (val payload = message.payload) {
                is InitiatorHelloMessage -> {
                    val partitionsAssigned =
                        inboundAssignmentListener.getCurrentlyAssignedPartitions()
                    if (partitionsAssigned.isNotEmpty()) {
                        listOf(
                            Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), response),
                            Record(
                                Schemas.P2P.SESSION_OUT_PARTITIONS,
                                payload.header.sessionId,
                                SessionPartitions(partitionsAssigned.toList())
                            )
                        )
                    } else {
                        logger.warn(
                            "No partitions from topic ${Schemas.P2P.LINK_IN_TOPIC} are currently assigned to " +
                                "the inbound message processor." +
                                " Not going to reply to session initiation for session ${payload.header.sessionId}."
                        )
                        emptyList()
                    }
                }
                else -> {
                    listOf(Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), response))
                }
            }
        } else {
            emptyList()
        }
    }

    private fun processDataMessage(sessionId: String, message: AvroSealedClasses.DataMessage): List<Record<*, *>> {
        val messages = mutableListOf<Record<*, *>>()
        when (val sessionDirection = sessionManager.getSessionById(sessionId)) {
            is SessionManager.SessionDirection.Inbound -> {
                messages.addAll(
                    processLinkManagerPayload(
                        sessionDirection.counterparties,
                        sessionDirection.session,
                        sessionId,
                        message
                    )
                )
            }
            is SessionManager.SessionDirection.Outbound -> {
                MessageConverter.extractPayload(
                    sessionDirection.session,
                    sessionId,
                    message,
                    MessageAck::fromByteBuffer
                )?.let {
                    when (val ack = it.ack) {
                        is AuthenticatedMessageAck -> {
                            logger.debug { "Processing ack for message ${ack.messageId} from session $sessionId." }
                            sessionManager.messageAcknowledged(sessionId)
                            messages.add(makeMarkerForAckMessage(ack))
                        }
                        is HeartbeatMessageAck -> {
                            logger.debug { "Processing heartbeat ack from session $sessionId." }
                            sessionManager.messageAcknowledged(sessionId)
                        }
                        else -> logger.warn("Received an inbound message with unexpected type for SessionId = $sessionId.")
                    }
                }
            }
            is SessionManager.SessionDirection.NoSession -> {
                logger.warn(
                    "Received message with SessionId = $sessionId for which there is no active session." +
                        " The message was discarded."
                )
            }
        }
        return messages
    }

    private fun checkIdentityBeforeProcessing(
        counterparties: SessionManager.SessionCounterparties,
        innerMessage: AuthenticatedMessageAndKey,
        session: Session,
        messages: MutableList<Record<*, *>>
    ) {
        val sessionSource = counterparties.counterpartyId
        val sessionDestination = counterparties.ourId
        val messageDestination = innerMessage.message.header.destination
        val messageSource = innerMessage.message.header.source
        if (sessionSource == messageSource.toCorda() && sessionDestination == messageDestination.toCorda()) {
            logger.debug {
                "Processing message ${innerMessage.message.header.messageId} " +
                    "of type ${innerMessage.message.javaClass} from session ${session.sessionId}"
            }
            messages.add(Record(Schemas.P2P.P2P_IN_TOPIC, innerMessage.key, AppMessage(innerMessage.message)))
            makeAckMessageForFlowMessage(innerMessage.message, session)?.let { ack -> messages.add(ack) }
            sessionManager.inboundSessionEstablished(session.sessionId)
        } else if (sessionSource != messageSource.toCorda()) {
            logger.warn(
                "The identity in the message's source header ($messageSource)" +
                    " does not match the session's source identity ($sessionSource)," +
                    " which indicates a spoofing attempt! The message was discarded."
            )
        } else {
            logger.warn(
                "The identity in the message's destination header ($messageDestination)" +
                    " does not match the session's destination identity ($sessionDestination)," +
                    " which indicates a spoofing attempt! The message was discarded"
            )
        }
    }

    private fun processLinkManagerPayload(
        counterparties: SessionManager.SessionCounterparties,
        session: Session,
        sessionId: String,
        message: AvroSealedClasses.DataMessage
    ): MutableList<Record<*, *>> {
        val messages = mutableListOf<Record<*, *>>()
        MessageConverter.extractPayload(session, sessionId, message, DataMessagePayload::fromByteBuffer)?.let {
            when (val innerMessage = it.message) {
                is HeartbeatMessage -> {
                    logger.debug { "Processing heartbeat message from session $sessionId" }
                    makeAckMessageForHeartbeatMessage(counterparties, session)?.let { ack -> messages.add(ack) }
                }
                is AuthenticatedMessageAndKey -> {
                    checkIdentityBeforeProcessing(
                        counterparties,
                        innerMessage,
                        session,
                        messages
                    )
                }
                else -> logger.warn("Unknown incoming message type: ${innerMessage.javaClass}. The message was discarded.")
            }
        }
        return messages
    }

    private fun makeAckMessageForHeartbeatMessage(
        counterparties: SessionManager.SessionCounterparties,
        session: Session
    ): Record<String, LinkOutMessage>? {
        val ackDest = counterparties.counterpartyId
        val ackSource = counterparties.ourId
        val ack = MessageConverter.linkOutMessageFromAck(
            MessageAck(HeartbeatMessageAck()),
            ackSource,
            ackDest,
            session,
            groupPolicyProvider,
            members,
        ) ?: return null
        return Record(
            Schemas.P2P.LINK_OUT_TOPIC,
            LinkManager.generateKey(),
            ack
        )
    }

    private fun makeAckMessageForFlowMessage(
        message: AuthenticatedMessage,
        session: Session
    ): Record<String, LinkOutMessage>? {
        // We route the ACK back to the original source
        val ackDest = message.header.source.toCorda()
        val ackSource = message.header.destination.toCorda()
        val ack = MessageConverter.linkOutMessageFromAck(
            MessageAck(AuthenticatedMessageAck(message.header.messageId)),
            ackSource,
            ackDest,
            session,
            groupPolicyProvider,
            members
        ) ?: return null
        return Record(
            Schemas.P2P.LINK_OUT_TOPIC,
            LinkManager.generateKey(),
            ack
        )
    }

    private fun makeMarkerForAckMessage(message: AuthenticatedMessageAck): Record<String, AppMessageMarker> {
        return Record(
            Schemas.P2P.P2P_OUT_MARKERS,
            message.messageId,
            AppMessageMarker(LinkManagerReceivedMarker(), clock.instant().toEpochMilli())
        )
    }

    override val keyClass = String::class.java
    override val valueClass = LinkInMessage::class.java
}
