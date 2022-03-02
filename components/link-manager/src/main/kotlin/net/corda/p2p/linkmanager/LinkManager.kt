package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses.DataMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.extractPayload
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutFromUnauthenticatedMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromAck
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromAuthenticatedMessageAndKey
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionDirection
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionCounterparties
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
import net.corda.schema.Schemas.P2P.Companion.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

@Suppress("LongParameterList")
class LinkManager(@Reference(service = SubscriptionFactory::class)
                  val subscriptionFactory: SubscriptionFactory,
                  @Reference(service = PublisherFactory::class)
                  val publisherFactory: PublisherFactory,
                  @Reference(service = LifecycleCoordinatorFactory::class)
                  val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
                  @Reference(service = ConfigurationReadService::class)
                  val configurationReaderService: ConfigurationReadService,
                  private val configuration: SmartConfig,
                  private val instanceId: Int,
                  val linkManagerNetworkMap: LinkManagerNetworkMap
                      = StubNetworkMap(lifecycleCoordinatorFactory, subscriptionFactory, instanceId, configuration),
                  private val linkManagerHostingMap: LinkManagerHostingMap
                      = ConfigBasedLinkManagerHostingMap(configurationReaderService, lifecycleCoordinatorFactory),
                  linkManagerCryptoProcessor: CryptoProcessor
                      = StubCryptoProcessor(lifecycleCoordinatorFactory, subscriptionFactory, instanceId, configuration)
) : LifecycleWithDominoTile {

    companion object {
        const val LINK_MANAGER_PUBLISHER_CLIENT_ID = "linkmanager"
        const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
        const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"

        fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    private var inboundAssignmentListener = InboundAssignmentListener(lifecycleCoordinatorFactory)

    private val messagesPendingSession = PendingSessionMessageQueuesImpl(
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration
    )

    private val sessionManager = SessionManagerImpl(
        linkManagerNetworkMap,
        linkManagerCryptoProcessor,
        messagesPendingSession,
        publisherFactory,
        configurationReaderService,
        lifecycleCoordinatorFactory,
        configuration,
        linkManagerHostingMap,
    )

    private val outboundMessageProcessor = OutboundMessageProcessor(
        sessionManager,
        linkManagerHostingMap,
        linkManagerNetworkMap,
        inboundAssignmentListener,
    )

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
        instanceId,
    ).also {
        linkManagerNetworkMap.registerListener(it)
    }

    private val tlsCertificatesPublisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
        instanceId,
    ).also {
        linkManagerHostingMap.registerListener(it)
    }

    private val deliveryTracker = DeliveryTracker(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        configuration,
        subscriptionFactory,
        linkManagerNetworkMap,
        linkManagerCryptoProcessor,
        sessionManager,
        instanceId
    ) { outboundMessageProcessor.processAuthenticatedMessage(it, true) }

    private val inboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, LINK_IN_TOPIC, instanceId),
        InboundMessageProcessor(sessionManager, linkManagerNetworkMap, inboundAssignmentListener),
        configuration,
        partitionAssignmentListener = inboundAssignmentListener
    )

    private val outboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, P2P_OUT_TOPIC, instanceId),
        outboundMessageProcessor,
        configuration,
        partitionAssignmentListener = null
    )

    private val commonChildren = setOf(linkManagerNetworkMap.dominoTile, linkManagerCryptoProcessor.dominoTile,
        linkManagerHostingMap.dominoTile)
    private val inboundSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        inboundMessageSubscription,
        dependentChildren = commonChildren,
        managedChildren = setOf(inboundAssignmentListener.dominoTile)
    )
    private val outboundSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        outboundMessageSubscription,
        dependentChildren = commonChildren + setOf(messagesPendingSession.dominoTile, inboundAssignmentListener.dominoTile),
        managedChildren = setOf(messagesPendingSession.dominoTile)
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(
            inboundSubscriptionTile,
            outboundSubscriptionTile,
            deliveryTracker.dominoTile,
        ),
        managedChildren = setOf(
            inboundSubscriptionTile,
            outboundSubscriptionTile,
            deliveryTracker.dominoTile,
            sessionManager.dominoTile,
            trustStoresPublisher.dominoTile,
            tlsCertificatesPublisher.dominoTile,
        ) + commonChildren
    )

    class OutboundMessageProcessor(
        private val sessionManager: SessionManager,
        private val linkManagerHostingMap: LinkManagerHostingMap,
        private val networkMap: LinkManagerNetworkMap,
        private val inboundAssignmentListener: InboundAssignmentListener,
    ) : EventLogProcessor<String, AppMessage> {

        override val keyClass = String::class.java
        override val valueClass = AppMessage::class.java
        private var logger = LoggerFactory.getLogger(this::class.java.name)

        override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, *>>()
            for (event in events) {
                records += processEvent(event)
            }
            return records
        }

        private fun processEvent(event: EventLogRecord<String, AppMessage>): List<Record<String, *>> {

            val message = event.value?.message
            if (message == null) {
                logger.error("Received null message. The message was discarded.")
                return emptyList()
            }

            return when (message) {
                is AuthenticatedMessage -> {
                    processAuthenticatedMessage(AuthenticatedMessageAndKey(message, event.key))
                }
                is UnauthenticatedMessage -> {
                    processUnauthenticatedMessage(message)
                }
                else -> {
                    logger.warn("Unknown message type: ${message::class.java}")
                    emptyList()
                }
            }
        }

        private fun processUnauthenticatedMessage(message: UnauthenticatedMessage): List<Record<String, *>> {
            logger.debug { "Processing outbound ${message.javaClass} to ${message.header.destination.toHoldingIdentity()}." }
            return if (linkManagerHostingMap.isHostedLocally(message.header.destination.toHoldingIdentity())) {
                listOf(Record(P2P_IN_TOPIC, generateKey(), AppMessage(message)))
            } else {
                val linkOutMessage = linkOutFromUnauthenticatedMessage(message, networkMap)
                listOf(Record(LINK_OUT_TOPIC, generateKey(), linkOutMessage))
            }
        }

        /**
         * processed an AuthenticatedMessage returning a list of records to be persisted.
         *
         * [isReplay] - If the message is being replayed we don't persist a [LinkManagerSentMarker] as there is already
         * a marker for this message. If the process is restarted we reread the original marker.
         */
        fun processAuthenticatedMessage(
            messageAndKey: AuthenticatedMessageAndKey,
            isReplay: Boolean = false
        ): List<Record<String, *>> {
            logger.trace{ "Processing outbound ${messageAndKey.message.javaClass} with ID ${messageAndKey.message.header.messageId} " +
                    "to ${messageAndKey.message.header.destination.toHoldingIdentity()}." }
            val isHostedLocally = linkManagerHostingMap.isHostedLocally(messageAndKey.message.header.destination.toHoldingIdentity())
            return if (isHostedLocally) {
                mutableListOf(Record(P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)))
            } else {
                when (val state = sessionManager.processOutboundMessage(messageAndKey)) {
                    is SessionState.NewSessionNeeded -> {
                        logger.trace { "No existing session with ${messageAndKey.message.header.destination.toHoldingIdentity()}. " +
                                "Initiating a new one.." }
                        recordsForNewSession(state)
                    }
                    is SessionState.SessionEstablished -> {
                        logger.trace { "Session already established with ${messageAndKey.message.header.destination.toHoldingIdentity()}." +
                                " Using this to send outbound message." }
                        recordsForSessionEstablished(state, messageAndKey)
                    }
                    is SessionState.SessionAlreadyPending, SessionState.CannotEstablishSession -> {
                        logger.trace { "Session already pending with ${messageAndKey.message.header.destination.toHoldingIdentity()}. " +
                                "Message queued until session is established." }
                        emptyList()
                    }
                }
            } + if (!isReplay) recordsForMarkers(messageAndKey, isHostedLocally) else emptyList()
        }

        private fun recordsForNewSession(state: SessionState.NewSessionNeeded): List<Record<String, *>> {
            val partitions = inboundAssignmentListener.getCurrentlyAssignedPartitions(LINK_IN_TOPIC).toList()
            return if(partitions.isEmpty()) {
                logger.warn("No partitions from topic $LINK_IN_TOPIC are currently assigned to the inbound message processor." +
                        " Session ${state.sessionId} will not be initiated.")
                emptyList()
            } else {
                listOf(
                    Record(LINK_OUT_TOPIC, generateKey(), state.sessionInitMessage),
                    Record(SESSION_OUT_PARTITIONS, state.sessionId, SessionPartitions(partitions))
                )
            }
        }

        private fun recordsForSessionEstablished(
            state: SessionState.SessionEstablished,
            messageAndKey: AuthenticatedMessageAndKey
        ): List<Record<String, *>> {
            return recordsForSessionEstablished(sessionManager, networkMap, state.session, messageAndKey)
        }

        private fun recordsForMarkers(messageAndKey: AuthenticatedMessageAndKey, isHostedLocally: Boolean): List<Record<String, *>> {
            val markers = mutableListOf(recordForLMSentMarker(messageAndKey, messageAndKey.message.header.messageId))
            if (isHostedLocally) markers += listOf(recordForLMReceivedMarker(messageAndKey.message.header.messageId))
            return markers
        }

        private fun recordForLMSentMarker(message: AuthenticatedMessageAndKey, messageId: String): Record<String, AppMessageMarker> {
            val marker = AppMessageMarker(LinkManagerSentMarker(message), Instant.now().toEpochMilli())
            return Record(P2P_OUT_MARKERS, messageId, marker)
        }

        private fun recordForLMReceivedMarker(messageId: String): Record<String, AppMessageMarker> {
            val marker = AppMessageMarker(LinkManagerReceivedMarker(), Instant.now().toEpochMilli())
            return Record(P2P_OUT_MARKERS, messageId, marker)
        }
    }

    class InboundMessageProcessor(
        private val sessionManager: SessionManager,
        private val networkMap: LinkManagerNetworkMap,
        private val inboundAssignmentListener: InboundAssignmentListener
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
                    is AuthenticatedDataMessage -> processDataMessage(payload.header.sessionId, DataMessage.Authenticated(payload))
                    is AuthenticatedEncryptedDataMessage -> processDataMessage(
                        payload.header.sessionId,
                        DataMessage.AuthenticatedAndEncrypted(payload)
                    )
                    is ResponderHelloMessage, is ResponderHandshakeMessage,
                    is InitiatorHandshakeMessage, is InitiatorHelloMessage -> processSessionMessage(message)
                    is UnauthenticatedMessage -> {
                        listOf(Record(P2P_IN_TOPIC, generateKey(), payload))
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
                            inboundAssignmentListener.getCurrentlyAssignedPartitions(LINK_IN_TOPIC).toList()
                        if (partitionsAssigned.isNotEmpty()) {
                            listOf(
                                Record(LINK_OUT_TOPIC, generateKey(), response),
                                Record(
                                    SESSION_OUT_PARTITIONS,
                                    payload.header.sessionId,
                                    SessionPartitions(partitionsAssigned)
                                )
                            )
                        } else {
                            logger.warn(
                                "No partitions from topic ${LINK_IN_TOPIC} are currently assigned to the inbound message processor." +
                                        " Not going to reply to session initiation for session ${payload.header.sessionId}."
                            )
                            emptyList()
                        }
                    }
                    else -> {
                        listOf(Record(LINK_OUT_TOPIC, generateKey(), response))
                    }
                }
            } else {
                emptyList()
            }
        }

        private fun processDataMessage(sessionId: String, message: DataMessage): List<Record<*, *>> {
            val messages = mutableListOf<Record<*, *>>()
            when (val sessionDirection = sessionManager.getSessionById(sessionId)) {
                is SessionDirection.Inbound -> {
                    messages.addAll(
                        processLinkManagerPayload(sessionDirection.counterparties, sessionDirection.session, sessionId, message)
                    )
                }
                is SessionDirection.Outbound -> {
                    extractPayload(sessionDirection.session, sessionId, message, MessageAck::fromByteBuffer)?.let {
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
                is SessionDirection.NoSession -> {
                    logger.warn("Received message with SessionId = $sessionId for which there is no active session." +
                            " The message was discarded.")
                }
            }
            return messages
        }

        private fun checkIdentityBeforeProcessing(
            counterparties: SessionCounterparties,
            innerMessage: AuthenticatedMessageAndKey,
            session: Session,
            messages: MutableList<Record<*, *>>
        )
        {
            val sessionSource = counterparties.counterpartyId.toHoldingIdentity()
            val sessionDestination = counterparties.ourId.toHoldingIdentity()
            val messageDestination = innerMessage.message.header.destination
            val messageSource = innerMessage.message.header.source
            if(sessionSource == messageSource && sessionDestination == messageDestination) {
                logger.debug { "Processing message ${innerMessage.message.header.messageId} " +
                        "of type ${innerMessage.message.javaClass} from session ${session.sessionId}" }
                messages.add(Record(P2P_IN_TOPIC, innerMessage.key, AppMessage(innerMessage.message)))
                makeAckMessageForFlowMessage(innerMessage.message, session)?.let { ack -> messages.add(ack) }
                sessionManager.inboundSessionEstablished(session.sessionId)
            } else if(sessionSource != messageSource) {
                logger.warn("The identity in the message's source header ($messageSource)" +
                        " does not match the session's source identity ($sessionSource)," +
                        " which indicates a spoofing attempt! The message was discarded.")
            } else {
                logger.warn("The identity in the message's destination header ($messageDestination)" +
                        " does not match the session's destination identity ($sessionDestination)," +
                        " which indicates a spoofing attempt! The message was discarded")
            }
        }

        private fun processLinkManagerPayload(
            counterparties: SessionCounterparties,
            session: Session,
            sessionId: String,
            message: DataMessage
        ): MutableList<Record<*, *>> {
            val messages = mutableListOf<Record<*, *>>()
            extractPayload(session, sessionId, message, DataMessagePayload::fromByteBuffer)?.let {
                when (val innerMessage = it.message) {
                    is HeartbeatMessage -> {
                        logger.debug {"Processing heartbeat message from session $sessionId"}
                        makeAckMessageForHeartbeatMessage(counterparties, session)?.let { ack -> messages.add(ack) }
                    }
                    is AuthenticatedMessageAndKey -> {
                        checkIdentityBeforeProcessing(
                            counterparties,
                            innerMessage,
                            session,
                            messages)
                    }
                    else -> logger.warn("Unknown incoming message type: ${innerMessage.javaClass}. The message was discarded.")
                }
            }
            return messages
        }

        private fun makeAckMessageForHeartbeatMessage(
            counterparties: SessionCounterparties,
            session: Session
        ): Record<String, LinkOutMessage>? {
            val ackDest = counterparties.counterpartyId.toHoldingIdentity()
            val ackSource = counterparties.ourId.toHoldingIdentity()
            val ack = linkOutMessageFromAck(
                MessageAck(HeartbeatMessageAck()),
                ackSource,
                ackDest,
                session,
                networkMap
            ) ?: return null
            return Record(
                LINK_OUT_TOPIC,
                generateKey(),
                ack
            )
        }

        private fun makeAckMessageForFlowMessage(message: AuthenticatedMessage, session: Session): Record<String, LinkOutMessage>? {
            //We route the ACK back to the original source
            val ackDest = message.header.source
            val ackSource = message.header.destination
            val ack = linkOutMessageFromAck(
                MessageAck(AuthenticatedMessageAck(message.header.messageId)),
                ackSource,
                ackDest,
                session,
                networkMap
            ) ?: return null
            return Record(
                LINK_OUT_TOPIC,
                generateKey(),
                ack
            )
        }

        private fun makeMarkerForAckMessage(message: AuthenticatedMessageAck): Record<String, AppMessageMarker> {
            return Record(
                P2P_OUT_MARKERS,
                message.messageId,
                AppMessageMarker(LinkManagerReceivedMarker(), Instant.now().toEpochMilli())
            )
        }

        override val keyClass = String::class.java
        override val valueClass = LinkInMessage::class.java
    }

    interface PendingSessionMessageQueues: LifecycleWithDominoTile {
        fun queueMessage(message: AuthenticatedMessageAndKey, counterparties: SessionCounterparties)
        fun sessionNegotiatedCallback(
            sessionManager: SessionManager,
            counterparties: SessionCounterparties,
            session: Session,
            networkMap: LinkManagerNetworkMap,
        )
        fun destroyQueue(counterparties: SessionCounterparties)
        fun destroyAllQueues()
    }

    class PendingSessionMessageQueuesImpl(
        publisherFactory: PublisherFactory,
        coordinatorFactory: LifecycleCoordinatorFactory,
        configuration: SmartConfig
    ): PendingSessionMessageQueues {

        companion object {
            private val logger = contextLogger()
        }

        private val queuedMessagesPendingSession = HashMap<SessionCounterparties, Queue<AuthenticatedMessageAndKey>>()
        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
            PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID),
            configuration
        )
        override val dominoTile = publisher.dominoTile

        /**
         * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
         * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
        */
        override fun queueMessage(message: AuthenticatedMessageAndKey, counterparties: SessionCounterparties) {
            val oldQueue = queuedMessagesPendingSession.putIfAbsent(counterparties, LinkedList())
            if (oldQueue != null) {
                oldQueue.add(message)
            } else {
                queuedMessagesPendingSession[counterparties]?.add(message)
            }
        }

        /**
         * Publish all the queued [FlowMessage]s to the P2P_OUT_TOPIC.
         */
        override fun sessionNegotiatedCallback(
            sessionManager: SessionManager,
            counterparties: SessionCounterparties,
            session: Session,
            networkMap: LinkManagerNetworkMap,
        ) {
            publisher.dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("sessionNegotiatedCallback was called before the PendingSessionMessageQueues was started.")
                }
                val queuedMessages = queuedMessagesPendingSession[counterparties] ?: return@withLifecycleLock
                val records = mutableListOf<Record<String, *>>()
                while (queuedMessages.isNotEmpty()) {
                    val message = queuedMessages.poll()
                    logger.debug { "Sending queued message ${message.message.header.messageId} " +
                            "to newly established session ${session.sessionId} with ${counterparties.counterpartyId}" }
                    records.addAll(recordsForSessionEstablished(sessionManager, networkMap, session, message))
                }
                publisher.publish(records)
            }
        }

        override fun destroyQueue(counterparties: SessionCounterparties) {
            queuedMessagesPendingSession.remove(counterparties)
        }

        override fun destroyAllQueues() {
            queuedMessagesPendingSession.clear()
        }

    }
}

fun recordsForSessionEstablished(
    sessionManager: SessionManager,
    networkMap: LinkManagerNetworkMap,
    session: Session,
    messageAndKey: AuthenticatedMessageAndKey
): List<Record<String, *>> {
    val records = mutableListOf<Record<String, *>>()
    val key = LinkManager.generateKey()
    sessionManager.dataMessageSent(session)
    linkOutMessageFromAuthenticatedMessageAndKey(messageAndKey, session, networkMap)?. let {
        records.add(Record(LINK_OUT_TOPIC, key, it))
    }
    return records
}
