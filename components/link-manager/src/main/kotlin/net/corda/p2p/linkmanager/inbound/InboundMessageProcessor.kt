package net.corda.p2p.linkmanager.inbound

import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.AuthenticatedMessageAck
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.DataMessagePayload
import net.corda.data.p2p.HeartbeatMessage
import net.corda.data.p2p.HeartbeatMessageAck
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.SessionPartitions
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.AvroSealedClasses
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.membership.NetworkMessagingValidator
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.linkmanager.TraceableItem
import net.corda.p2p.linkmanager.metrics.recordInboundHeartbeatMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordInboundMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordInboundSessionMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordOutboundSessionMessagesMetric
import net.corda.schema.Schemas
import net.corda.tracing.traceEventProcessing
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class InboundMessageProcessor(
    private val sessionManager: SessionManager,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val clock: Clock,
    private val networkMessagingValidator: NetworkMessagingValidator =
        NetworkMessagingValidator(membershipGroupReaderProvider),
) :
    EventLogProcessor<String, LinkInMessage> {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.name)
        const val tracingEventName = "P2P Link Manager Inbound Event"
    }

    override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
        logger.info("QQQ onNext 1")
        val dataMessages = mutableListOf<SessionIdAndMessage>()
        val sessionMessages = mutableListOf<TraceableItem<LinkInMessage, LinkInMessage>>()
        val recordsForUnauthenticatedMessage = mutableListOf<TraceableItem<List<Record<String, AppMessage>>, LinkInMessage>>()

        events.forEach { event ->
            val message = event.value
            when (val payload = message?.payload) {
                is AuthenticatedDataMessage -> {
                    payload.header.sessionId.let { sessionId ->
                        logger.info("QQQ onNext 2 \t $sessionId")
                        dataMessages.add(
                            SessionIdAndMessage(sessionId,
                                TraceableItem(AvroSealedClasses.DataMessage.Authenticated(payload), event)
                            )
                        )
                    }
                }
                is AuthenticatedEncryptedDataMessage -> {
                    payload.header.sessionId.let { sessionId ->
                        dataMessages.add(
                            SessionIdAndMessage(sessionId,
                                TraceableItem(AvroSealedClasses.DataMessage.AuthenticatedAndEncrypted(payload), event)
                            )
                        )
                    }
                }
                is ResponderHelloMessage, is ResponderHandshakeMessage, is InitiatorHandshakeMessage, is InitiatorHelloMessage -> {
                    sessionMessages.add(
                        TraceableItem(message, event)
                    )
                }
                is InboundUnauthenticatedMessage -> {
                    logger.debug {
                        "Processing unauthenticated message ${payload.header.messageId}"
                    }
                    recordInboundMessagesMetric(payload)
                    recordsForUnauthenticatedMessage.add(
                        TraceableItem(
                            listOf(
                                Record(
                                    Schemas.P2P.P2P_IN_TOPIC,
                                    LinkManager.generateKey(),
                                    AppMessage(payload),
                                )
                            ),
                            event,
                        )
                    )

                }
                null -> logger.error("Received null message. The message was discarded.")
                else -> {
                    logger.error("Received unknown payload type ${payload::class.java.simpleName}. The message was discarded.")
                }
            }
        }

        return (processSessionMessages(sessionMessages) + processDataMessages(dataMessages) + recordsForUnauthenticatedMessage)
            .flatMap { traceable ->
                traceable.originalRecord?.let { traceEventProcessing(it, tracingEventName) { traceable.item } }
                traceable.item
            }
    }

    private fun processSessionMessages(messages: List<TraceableItem<LinkInMessage, LinkInMessage>>):
            List<TraceableItem<List<Record<String, *>>, LinkInMessage>> {
        recordInboundSessionMessagesMetric(messages.size)
        val responses = sessionManager.processSessionMessages(messages) { message ->
            message.item
        }
        responses.also {
            if (it.size != messages.size) {
                logger.info("TTT processSessionMessages(${messages.size}) != ${it.size}")
            }
            val sent = messages.toSet()
            val returned = it.map { it.first }.toSet()
            if (sent != returned) {
                logger.info("TTT processSessionMessages sent != returned")
                sent.forEach { s ->
                    if (!returned.contains(s)) {
                        logger.info("TTT processSessionMessages missing s $s")
                    }
                }
                returned.forEach { r ->
                    if (!sent.contains(r)) {
                        logger.info("TTT processSessionMessages missing r $r")
                    }
                }
            }
        }
        return responses.map { (traceableMessage, response) ->
            if (response != null) {
                when (val payload = response.payload) {
                    is ResponderHelloMessage -> {
                        val partitionsAssigned = inboundAssignmentListener.getCurrentlyAssignedPartitions()
                        if (partitionsAssigned.isNotEmpty()) {
                            recordOutboundSessionMessagesMetric(response.header.sourceIdentity, response.header.destinationIdentity)
                            TraceableItem(
                                listOf(
                                    Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), response),
                                    Record(
                                        Schemas.P2P.SESSION_OUT_PARTITIONS,
                                        payload.header.sessionId,
                                        SessionPartitions(partitionsAssigned.toList())
                                    )
                                ),
                                traceableMessage.originalRecord
                            )
                        } else {
                            logger.warn(
                                "No partitions from topic ${Schemas.P2P.LINK_IN_TOPIC} are currently assigned to " +
                                        "the inbound message processor." +
                                        " Not going to reply to session initiation for session ${payload.header.sessionId}."
                            )
                            TraceableItem(emptyList(), traceableMessage.originalRecord)
                        }
                    }
                    else -> {
                        recordOutboundSessionMessagesMetric(response.header.sourceIdentity, response.header.destinationIdentity)
                        TraceableItem(
                            listOf(Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), response)),
                            traceableMessage.originalRecord
                        )
                    }
                }
            } else {
                TraceableItem(emptyList(), traceableMessage.originalRecord)
            }
        }
    }

    internal data class SessionIdAndMessage(
        val sessionId: String,
        val message: TraceableItem<out AvroSealedClasses.DataMessage, LinkInMessage>
    )

    private fun processDataMessages(
        sessionIdAndMessages: List<SessionIdAndMessage>
    ): List<TraceableItem<List<Record<*, *>>, LinkInMessage>> {
        logger.info("QQQ processDataMessages 1")
        return sessionManager.getSessionsById(sessionIdAndMessages) { it.sessionId }
            .also {
                if (it.size != sessionIdAndMessages.size) {
                    logger.info("TTT processDataMessages(${sessionIdAndMessages.size}) != ${it.size}")
                }
                val sent = sessionIdAndMessages.toSet()
                val returned = it.map { it.first }.toSet()
                if (sent != returned) {
                    logger.info("TTT processDataMessages sent != returned")
                    sent.forEach { s ->
                        if (!returned.contains(s)) {
                            logger.info("TTT processDataMessages missing s $s")
                        }
                    }
                    returned.forEach { r ->
                        if (!sent.contains(r)) {
                            logger.info("TTT processDataMessages missing r $r")
                        }
                    }
                }
            }
            .mapNotNull { (sessionIdAndMessage, sessionDirection) ->
            logger.info("QQQ processDataMessages \t 2 - sessionDirection: $sessionDirection ")
            logger.info("QQQ processDataMessages \t 2 - sessionId: ${sessionIdAndMessage.sessionId} ")
            logger.info("QQQ processDataMessages \t 2 - key: ${sessionIdAndMessage.message.originalRecord?.key} ")
            when (sessionDirection) {
                is SessionManager.SessionDirection.Inbound ->
                    TraceableItem(
                        processInboundDataMessages(sessionIdAndMessage, sessionDirection),
                        sessionIdAndMessage.message.originalRecord
                    )
                is SessionManager.SessionDirection.Outbound -> processOutboundDataMessage(sessionIdAndMessage, sessionDirection)?.let {
                    TraceableItem(listOf(it), sessionIdAndMessage.message.originalRecord)
                }
                is SessionManager.SessionDirection.NoSession -> {
                    logger.warn("Received message with SessionId = ${sessionIdAndMessage.sessionId} for which there is no active session." +
                            " The message was discarded."
                    )
                    null
                }
            }
        }
    }

    private fun processInboundDataMessages(
        sessionIdAndMessage: SessionIdAndMessage,
        sessionDirection: SessionManager.SessionDirection.Inbound
    ): List<Record<*, *>> {
        logger.info("QQQ processInboundDataMessages: ${sessionIdAndMessage.sessionId}")
        sessionManager.dataMessageReceived(
            sessionIdAndMessage.sessionId,
            sessionDirection.counterparties.counterpartyId,
            sessionDirection.counterparties.ourId
        )
        return if (isCommunicationAllowed(sessionDirection.counterparties)) {
            processLinkManagerPayload(
                sessionDirection.counterparties,
                sessionDirection.session,
                sessionIdAndMessage.sessionId,
                sessionIdAndMessage.message.item
            ).also {
                it.forEach {
                    logger.info("QQQ processInboundDataMessages \t ${it.key}, ${it.value?.javaClass?.simpleName}")
                }
            }
        } else {
            emptyList()
        }
    }

    private fun processOutboundDataMessage(
        sessionIdAndMessage: SessionIdAndMessage,
        sessionDirection: SessionManager.SessionDirection.Outbound
    ): Record<*, *>?  {
        return if (isCommunicationAllowed(sessionDirection.counterparties)) {
            MessageConverter.extractPayload(
                sessionDirection.session,
                sessionIdAndMessage.sessionId,
                sessionIdAndMessage.message.item,
                MessageAck::fromByteBuffer
            )?.let {
                when (val ack = it.ack) {
                    is AuthenticatedMessageAck -> {
                        logger.info("QQQ Got ack for ${ack.messageId}")
                        logger.debug { "Processing ack for message ${ack.messageId} from session $sessionIdAndMessage." }
                        sessionManager.messageAcknowledged(sessionIdAndMessage.sessionId)
                        val record = makeMarkerForAckMessage(ack)
                        record
                    }
                    is HeartbeatMessageAck -> {
                        logger.debug { "Processing heartbeat ack from session $sessionIdAndMessage." }
                        sessionManager.messageAcknowledged(sessionIdAndMessage.sessionId)
                        null
                    }
                    else -> {
                        logger.warn("Received an inbound message with unexpected type for SessionId = $sessionIdAndMessage.")
                        null
                    }
                }
            }
        } else {
            null
        }
    }


    private fun checkIdentityBeforeProcessing(
        counterparties: SessionManager.Counterparties,
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
            logger.info("QQQ posting ack for ${innerMessage.message.header.messageId}")
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
        counterparties: SessionManager.Counterparties,
        session: Session,
        sessionId: String,
        message: AvroSealedClasses.DataMessage
    ): MutableList<Record<*, *>> {
        val messages = mutableListOf<Record<*, *>>()
        logger.info("QQQ processLinkManagerPayload for session ID: $sessionId")
        MessageConverter.extractPayload(session, sessionId, message, DataMessagePayload::fromByteBuffer)?.let {
            logger.info("QQQ processLinkManagerPayload \t it.message: ${it.message.javaClass.simpleName}")
            when (val innerMessage = it.message) {
                is HeartbeatMessage -> {
                    logger.debug { "Processing heartbeat message from session $sessionId" }
                    recordInboundHeartbeatMessagesMetric(counterparties.counterpartyId, counterparties.ourId)
                    makeAckMessageForHeartbeatMessage(counterparties, session)?.let { ack -> messages.add(ack) }
                }
                is AuthenticatedMessageAndKey -> {
                    recordInboundMessagesMetric(innerMessage.message)
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
        counterparties: SessionManager.Counterparties,
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
            membershipGroupReaderProvider,
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
        logger.info("QQQ Sending ack for ${message.header.messageId} from $ackSource to $ackDest")
        val ack = MessageConverter.linkOutMessageFromAck(
            MessageAck(AuthenticatedMessageAck(message.header.messageId)),
            ackSource,
            ackDest,
            session,
            groupPolicyProvider,
            membershipGroupReaderProvider
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

    private fun isCommunicationAllowed(
        counterparties: SessionManager.Counterparties,
    ): Boolean = networkMessagingValidator.isValidInbound(
        counterparties.counterpartyId,
        counterparties.ourId,
    )


    override val keyClass = String::class.java
    override val valueClass = LinkInMessage::class.java
}
