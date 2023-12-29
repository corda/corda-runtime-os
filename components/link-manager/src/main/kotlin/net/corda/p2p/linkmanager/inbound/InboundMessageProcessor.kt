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
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.AvroSealedClasses
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.membership.NetworkMessagingValidator
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.p2p.linkmanager.metrics.recordInboundHeartbeatMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordInboundMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordInboundSessionMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordOutboundSessionMessagesMetric
import net.corda.schema.Schemas
import net.corda.tracing.traceEventProcessing
import net.corda.utilities.debug
import net.corda.utilities.flags.Features
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
    private val publisher: PublisherWithDominoLogic,
    private val clock: Clock,
    private val networkMessagingValidator: NetworkMessagingValidator =
        NetworkMessagingValidator(membershipGroupReaderProvider),
    private val featuers: Features = Features(),
) :
    EventLogProcessor<String, LinkInMessage>,
    SyncRPCProcessor<LinkInMessage, LinkManagerResponse>
{

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.name)
        const val TRACING_EVENT_NAME = "P2P Link Manager Inbound Event"
    }

    override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
        return events.flatMap { event ->
            traceEventProcessing(event, TRACING_EVENT_NAME) {
                val response = handleMessage(event.value)
                response?.let {
                    response.recordsToPost
                } ?: emptyList()
            }
        }

    }
    private data class Response(
        val recordsToPost: List<Record<*, *>>,
        val response: LinkManagerResponse = LinkManagerResponse(null),
    ) {
        fun addRecord(record: Record<*, *>)=
            Response(
                response = response,
                recordsToPost = recordsToPost + record
            )
    }

    private fun handleMessage(
        message: LinkInMessage?
    ) : Response? {
        return when (val payload = message?.payload) {
            is AuthenticatedDataMessage -> {
                val sessionId = payload.header.sessionId
                processDataMessage(
                    SessionIdAndMessage(
                        sessionId,
                        AvroSealedClasses.DataMessage.Authenticated(payload),
                    )
                )
            }
            is AuthenticatedEncryptedDataMessage -> {
                val sessionId = payload.header.sessionId
                processDataMessage(
                    SessionIdAndMessage(
                        sessionId,
                        AvroSealedClasses.DataMessage.AuthenticatedAndEncrypted(payload),
                    )
                )
            }
            is ResponderHelloMessage, is ResponderHandshakeMessage, is InitiatorHandshakeMessage, is InitiatorHelloMessage -> {
                return Response(
                    recordsToPost = processSessionMessage(message),
                )
            }
            is InboundUnauthenticatedMessage -> {
                logger.debug {
                    "Processing unauthenticated message ${payload.header.messageId}"
                }
                recordInboundMessagesMetric(payload)
                return Response(
                    recordsToPost = listOf(
                        Record(
                            Schemas.P2P.P2P_IN_TOPIC,
                            LinkManager.generateKey(),
                            AppMessage(payload),
                        )
                    ),
                )
            }
            null -> {
                logger.error("Received null message. The message was discarded.")
                null
            }
            else -> {
                logger.error("Received unknown payload type ${payload::class.java.simpleName}. The message was discarded.")
                null
            }
        }
    }

    private fun processSessionMessage(message: LinkInMessage):
            List<Record<String, *>> {
        recordInboundSessionMessagesMetric()
        val response = sessionManager.processSessionMessage(message)
        return if (response != null) {
            when (val payload = response.payload) {
                is ResponderHelloMessage -> {
                    val partitionsAssigned = inboundAssignmentListener.getCurrentlyAssignedPartitions()
                    if (partitionsAssigned.isNotEmpty()) {
                        recordOutboundSessionMessagesMetric(response.header.sourceIdentity, response.header.destinationIdentity)
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
                    recordOutboundSessionMessagesMetric(response.header.sourceIdentity, response.header.destinationIdentity)
                        listOf(Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), response))
                }
            }
        } else {
            emptyList()
        }
    }

    internal data class SessionIdAndMessage(
        val sessionId: String,
        val message: AvroSealedClasses.DataMessage
    )

    private fun processDataMessage(
        sessionIdAndMessage: SessionIdAndMessage
    ): Response? {
        return when (val sessionDirection = sessionManager.getSessionById(sessionIdAndMessage.sessionId)) {
            is SessionManager.SessionDirection.Inbound ->
                processInboundDataMessages(sessionIdAndMessage, sessionDirection)
            is SessionManager.SessionDirection.Outbound -> {
                Response(
                    recordsToPost = processOutboundDataMessage(sessionIdAndMessage, sessionDirection)?.let {
                        listOf(it)
                    }?: emptyList()
                )
            }
            is SessionManager.SessionDirection.NoSession -> {
                logger.warn("Received message with SessionId = ${sessionIdAndMessage.sessionId} for which there is no active session." +
                        " The message was discarded."
                )
                null
            }
        }
    }

    private fun processInboundDataMessages(
        sessionIdAndMessage: SessionIdAndMessage,
        sessionDirection: SessionManager.SessionDirection.Inbound
    ): Response? {
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
                sessionIdAndMessage.message
            )
        } else {
            null
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
                sessionIdAndMessage.message,
                MessageAck::fromByteBuffer
            )?.let {
                when (val ack = it.ack) {
                    is AuthenticatedMessageAck -> {
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
    ): Response? {
        val sessionSource = counterparties.counterpartyId
        val sessionDestination = counterparties.ourId
        val messageDestination = innerMessage.message.header.destination
        val messageSource = innerMessage.message.header.source
        return if (sessionSource == messageSource.toCorda() && sessionDestination == messageDestination.toCorda()) {
            logger.debug {
                "Processing message ${innerMessage.message.header.messageId} " +
                    "of type ${innerMessage.message.javaClass} from session ${session.sessionId}"
            }
            val ack = makeAckMessageForFlowMessage(innerMessage.message, session)
            sessionManager.inboundSessionEstablished(session.sessionId)
            ack.addRecord(
                Record(
                    Schemas.P2P.P2P_IN_TOPIC,
                    innerMessage.key,
                    AppMessage(innerMessage.message),
                )
            )
        } else if (sessionSource != messageSource.toCorda()) {
            logger.warn(
                "The identity in the message's source header ($messageSource)" +
                    " does not match the session's source identity ($sessionSource)," +
                    " which indicates a spoofing attempt! The message was discarded."
            )
            null
        } else {
            logger.warn(
                "The identity in the message's destination header ($messageDestination)" +
                    " does not match the session's destination identity ($sessionDestination)," +
                    " which indicates a spoofing attempt! The message was discarded"
            )
            null
        }
    }

    private fun processLinkManagerPayload(
        counterparties: SessionManager.Counterparties,
        session: Session,
        sessionId: String,
        message: AvroSealedClasses.DataMessage
    ): Response? {
        return MessageConverter.extractPayload(session, sessionId, message, DataMessagePayload::fromByteBuffer)?.let {
            when (val innerMessage = it.message) {
                is HeartbeatMessage -> {
                    logger.debug { "Processing heartbeat message from session $sessionId" }
                    recordInboundHeartbeatMessagesMetric(counterparties.counterpartyId, counterparties.ourId)
                    Response(
                        recordsToPost = makeAckMessageForHeartbeatMessage(counterparties, session)?.let { record ->
                            listOf(record)
                        }?: emptyList(),
                    )
                }
                is AuthenticatedMessageAndKey -> {
                    recordInboundMessagesMetric(innerMessage.message)
                    checkIdentityBeforeProcessing(
                        counterparties,
                        innerMessage,
                        session,
                    )
                }
                else -> {
                    logger.warn("Unknown incoming message type: ${innerMessage.javaClass}. The message was discarded.")
                    null
                }
            }
        }
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
        session: Session,
    ): Response {
        // We route the ACK back to the original source
        val ackDest = message.header.source.toCorda()
        val ackSource = message.header.destination.toCorda()
        val ackMessage = MessageAck(AuthenticatedMessageAck(message.header.messageId))
        return if (featuers.enableP2PGatewayToLinkManagerOverHttp) {
            Response(
                recordsToPost = emptyList(),
                response = MessageConverter.createLinkManagerResponse(ackMessage, session),
            )
        } else {
            val ack = MessageConverter.linkOutMessageFromAck(
                ackMessage,
                ackSource,
                ackDest,
                session,
                groupPolicyProvider,
                membershipGroupReaderProvider
            ) ?: return Response(
                recordsToPost = emptyList(),
            )
            val record = Record(
                Schemas.P2P.LINK_OUT_TOPIC,
                LinkManager.generateKey(),
                ack
            )
            Response(
                recordsToPost = listOf(record),
            )
        }
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
    override fun process(request: LinkInMessage): LinkManagerResponse {
        val response = handleMessage(request)
        if (response?.recordsToPost?.isNotEmpty() == true) {
            publisher.publish(response.recordsToPost).forEach {
                it.join()
            }
        }
        return response?.response?: LinkManagerResponse(null)
    }

    override val requestClass = LinkInMessage::class.java
    override val responseClass = LinkManagerResponse::class.java
}
