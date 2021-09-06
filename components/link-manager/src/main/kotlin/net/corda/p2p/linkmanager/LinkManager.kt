package net.corda.p2p.linkmanager

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.AuthenticatedMessageAndKey
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
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses.DataMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.extractPayload
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutFromUnauthenticatedMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromAck
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromFlowMessageAndKey
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionDirection
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.SessionKey
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.schema.Schema
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.schema.Schema.Companion.P2P_IN_TOPIC
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.time.Instant
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class LinkManager(@Reference(service = SubscriptionFactory::class)
                  subscriptionFactory: SubscriptionFactory,
                  @Reference(service = PublisherFactory::class)
                  publisherFactory: PublisherFactory,
                  linkManagerNetworkMap: LinkManagerNetworkMap,
                  linkManagerHostingMap: LinkManagerHostingMap,
                  linkManagerCryptoService: LinkManagerCryptoService,
                  config: LinkManagerConfig
) : Lifecycle {

    companion object {
        const val LINK_MANAGER_PUBLISHER_CLIENT_ID = "linkmanager"
        const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
        const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"

        fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private val outboundMessageSubscription: Subscription<String, AppMessage>
    private val inboundMessageSubscription: Subscription<String, LinkInMessage>
    private val inboundAssignmentListener = InboundAssignmentListener()

    private val messagesPendingSession = PendingSessionMessageQueuesImpl(publisherFactory)
    private val sessionReplayer: InMemorySessionReplayer =
        InMemorySessionReplayer(config.sessionMessageReplayPeriod, publisherFactory, linkManagerNetworkMap)
    private val sessionManager: SessionManager = SessionManagerImpl(
        SessionManagerImpl.ParametersForSessionNegotiation(config.maxMessageSize, config.protocolModes),
        linkManagerNetworkMap,
        linkManagerCryptoService,
        messagesPendingSession,
        sessionReplayer
    )
    private val messageReplayer: DeliveryTracker

    init {
        val outboundMessageSubscriptionConfig = SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Schema.P2P_OUT_TOPIC, 1)
        val outboundMessageProcessor = OutboundMessageProcessor(
            sessionManager,
            linkManagerHostingMap,
            linkManagerNetworkMap,
            inboundAssignmentListener
        )

        outboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            outboundMessageSubscriptionConfig,
            outboundMessageProcessor,
            partitionAssignmentListener = null
        )
        val inboundMessageSubscriptionConfig = SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Schema.LINK_IN_TOPIC, 1)
        inboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            inboundMessageSubscriptionConfig,
            InboundMessageProcessor(sessionManager, linkManagerNetworkMap),
            partitionAssignmentListener = inboundAssignmentListener
        )
        deliveryTracker = DeliveryTracker(
            config.flowMessageReplayPeriod,
            publisherFactory,
            subscriptionFactory
        ) { outboundMessageProcessor.processAuthenticatedMessage(it, false) }
    }

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                sessionReplayer.start()
                inboundMessageSubscription.start()
                /*We must wait for partitions to be assigned to the inbound subscription before we can start the outbound
                *subscription otherwise the gateway won't know which partition to route message back to.*/
                inboundAssignmentListener.awaitFirstAssignment()
                messageReplayer.start()
                outboundMessageSubscription.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                sessionReplayer.stop()
                inboundMessageSubscription.stop()
                outboundMessageSubscription.stop()
                running = false
            }
        }
    }

    override val isRunning: Boolean
        get() = running

    class OutboundMessageProcessor(
        private val sessionManager: SessionManager,
        private val linkManagerHostingMap: LinkManagerHostingMap,
        private val networkMap: LinkManagerNetworkMap,
        private val inboundAssignmentListener: InboundAssignmentListener
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
            return if (linkManagerHostingMap.isHostedLocally(message.header.destination.toHoldingIdentity())) {
                listOf(Record(P2P_IN_TOPIC, generateKey(), AppMessage(message)))
            } else {
                val linkOutMessage = linkOutFromUnauthenticatedMessage(message, networkMap)
                listOf(Record(Schema.LINK_OUT_TOPIC, generateKey(), linkOutMessage))
            }
        }

        fun processAuthenticatedMessage(
            messageAndKey: AuthenticatedMessageAndKey,
            addMarkers: Boolean = true
        ): List<Record<String, *>> {
            val isHostedLocally = linkManagerHostingMap.isHostedLocally(messageAndKey.message.header.destination.toHoldingIdentity())
            return if (isHostedLocally) {
                mutableListOf(Record(P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)))
            } else {
                when (val state = sessionManager.processOutboundFlowMessage(messageAndKey)) {
                    is SessionState.NewSessionNeeded -> recordsForNewSession(state)
                    is SessionState.SessionEstablished -> recordsForSessionEstablished(state, messageAndKey)
                    is SessionState.SessionAlreadyPending, SessionState.CannotEstablishSession -> emptyList()
                }
            } + if (addMarkers) recordsForMarkers(messageAndKey, isHostedLocally) else emptyList()
        }

        private fun recordsForNewSession(state: SessionState.NewSessionNeeded): List<Record<String, *>> {
            val records = mutableListOf<Record<String, *>>()
            records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), state.sessionInitMessage))
            val partitions = inboundAssignmentListener.getCurrentlyAssignedPartitions(Schema.LINK_IN_TOPIC).toList()
            records.add(Record(Schema.SESSION_OUT_PARTITIONS, state.sessionId, SessionPartitions(partitions)))
            return records
        }

        private fun recordsForSessionEstablished(
            state: SessionState.SessionEstablished,
            flowMessageAndKey: AuthenticatedMessageAndKey
        ): List<Record<String, *>> {
            val records = mutableListOf<Record<String, *>>()
            val message = linkOutMessageFromFlowMessageAndKey(flowMessageAndKey, state.session, networkMap) ?: return emptyList()
            records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), message))
            return records
        }

        private fun recordsForMarkers(messageAndKey: AuthenticatedMessageAndKey, isHostedLocally: Boolean): List<Record<String, *>> {
            val markers = mutableListOf(recordForLMSentMarker(messageAndKey, messageAndKey.message.header.messageId))
            if (isHostedLocally) markers += listOf(recordForLMReceivedMarker(messageAndKey.message.header.messageId))
            return markers
        }

        private fun recordForLMSentMarker(message: AuthenticatedMessageAndKey, messageId: String): Record<String, AppMessageMarker> {
            val marker = AppMessageMarker(LinkManagerSentMarker(message), Instant.now().toEpochMilli())
            return Record(Schema.P2P_OUT_MARKERS, messageId, marker)
        }

        private fun recordForLMReceivedMarker(messageId: String): Record<String, AppMessageMarker> {
            val marker = AppMessageMarker(LinkManagerReceivedMarker(), Instant.now().toEpochMilli())
            return Record(Schema.P2P_OUT_MARKERS, messageId, marker)
        }
    }

    class InboundMessageProcessor(private val sessionManager: SessionManager, private val networkMap: LinkManagerNetworkMap) :
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
                    is InitiatorHandshakeMessage -> processSessionMessage(message)
                    is UnauthenticatedMessage -> {
                        listOf(Record(Schema.P2P_IN_TOPIC, generateKey(), payload))
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
                listOf(Record(Schema.LINK_OUT_TOPIC, generateKey(), response))
            } else {
                emptyList()
            }
        }

        private fun processDataMessage(sessionId: String, message: DataMessage): List<Record<*, *>> {
            val messages = mutableListOf<Record<*, *>>()
            when (val sessionDirection = sessionManager.getSessionById(sessionId)) {
                is SessionDirection.Inbound -> {
                    extractPayload(sessionDirection.session, sessionId, message, AuthenticatedMessageAndKey::fromByteBuffer)?.let {
                        messages.add(Record(Schema.P2P_IN_TOPIC, it.key, AppMessage(it.message)))
                        makeAckMessageForFlowMessage(it.message, sessionDirection.session)?.let { ack -> messages.add(ack) }
                        sessionManager.acknowledgeInboundSessionNegotiation(sessionId)
                    }
                }
                is SessionDirection.Outbound -> {
                    extractPayload(sessionDirection.session, sessionId, message, MessageAck::fromByteBuffer)?.let {
                        messages.add(makeMarkerForAckMessage(it))
                    }
                }
                is SessionDirection.NoSession -> {
                    logger.warn("Received message with SessionId = $sessionId for which there is no active session." +
                            " The message was discarded.")
                }
            }
            return messages
        }

        private fun makeAckMessageForFlowMessage(message: AuthenticatedMessage, session: Session): Record<String, LinkOutMessage>? {
            //We route the ACK back to the original source
            val ackDest = message.header.source
            val ackSource = message.header.destination
            val ack = linkOutMessageFromAck(MessageAck(message.header.messageId), ackSource, ackDest, session, networkMap) ?: return null
            return Record(
                Schema.LINK_OUT_TOPIC,
                generateKey(),
                ack
            )
        }

        private fun makeMarkerForAckMessage(message: MessageAck): Record<String, AppMessageMarker> {
            return Record(
                Schema.P2P_OUT_MARKERS,
                message.messageId,
                AppMessageMarker(LinkManagerReceivedMarker(), Instant.now().toEpochMilli())
            )
        }

        override val keyClass = String::class.java
        override val valueClass = LinkInMessage::class.java
    }

    interface PendingSessionMessageQueues {
        fun queueMessage(message: AuthenticatedMessageAndKey, key: SessionKey): Boolean
        fun sessionNegotiatedCallback(key: SessionKey, session: Session, networkMap: LinkManagerNetworkMap)
    }

    class PendingSessionMessageQueuesImpl(publisherFactory: PublisherFactory): PendingSessionMessageQueues {
        private val queuedMessagesPendingSession = HashMap<SessionKey, Queue<AuthenticatedMessageAndKey>>()
        private val config = PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID, null)
        private val publisher = publisherFactory.createPublisher(config)

        /**
         * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
         * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
         * Returns [true] if we need to start session negotiation and [false] if we don't (if the session is pending).
        */
        override fun queueMessage(message: AuthenticatedMessageAndKey, key: SessionKey): Boolean {
            val oldQueue = queuedMessagesPendingSession.putIfAbsent(key, LinkedList())
            if (oldQueue != null) {
                oldQueue.add(message)
            } else {
                queuedMessagesPendingSession[key]?.add(message)
            }
            return oldQueue == null
        }

        /**
         * Publish all the queued [FlowMessage]s to the P2P_OUT_TOPIC.
         */
        override fun sessionNegotiatedCallback(key: SessionKey, session: Session, networkMap: LinkManagerNetworkMap) {
            val queuedMessages = queuedMessagesPendingSession[key] ?: return
            val records = mutableListOf<Record<String, LinkOutMessage>>()
            while (queuedMessages.isNotEmpty()) {
                val message = queuedMessages.poll()
                val dataMessage = linkOutMessageFromFlowMessageAndKey(message, session, networkMap)
                records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), dataMessage))
            }
            publisher.publish(records)
        }
    }
}
