package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
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
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionKey
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionDirection
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.schema.Schema
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.schema.Schema.Companion.P2P_IN_TOPIC
import net.corda.v5.base.annotations.VisibleForTesting
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.*
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

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
                      = StubNetworkMap(lifecycleCoordinatorFactory, subscriptionFactory, instanceId),
                  private val linkManagerHostingMap: LinkManagerHostingMap
                      = ConfigBasedLinkManagerHostingMap(configurationReaderService, lifecycleCoordinatorFactory),
                  private val linkManagerCryptoService: LinkManagerCryptoService
                      = StubCryptoService(lifecycleCoordinatorFactory, subscriptionFactory, instanceId)
) : LifecycleWithDominoTile {

    companion object {
        const val LINK_MANAGER_PUBLISHER_CLIENT_ID = "linkmanager"
        const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
        const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"

        fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    private val inboundAssigned = AtomicReference<CompletableFuture<Unit>>()
    private var inboundAssignmentListener = InboundAssignmentListener(inboundAssigned)

    private val messagesPendingSession = PendingSessionMessageQueuesImpl(
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
        instanceId
    )

    private val sessionManager = SessionManagerImpl(
        linkManagerNetworkMap,
        linkManagerCryptoService,
        messagesPendingSession,
        publisherFactory,
        configurationReaderService,
        lifecycleCoordinatorFactory,
        configuration,
        instanceId
    )

    private val outboundMessageProcessor = OutboundMessageProcessor(
        sessionManager,
        linkManagerHostingMap,
        linkManagerNetworkMap,
        inboundAssignmentListener,
    )

    private val deliveryTracker = DeliveryTracker(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        configuration,
        subscriptionFactory,
        linkManagerNetworkMap,
        linkManagerCryptoService,
        sessionManager,
        instanceId
    ) { outboundMessageProcessor.processAuthenticatedMessage(it, true) }

    @VisibleForTesting
    internal fun createInboundResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        inboundAssigned.set(future)
        val inboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Schema.LINK_IN_TOPIC, instanceId),
            InboundMessageProcessor(sessionManager, linkManagerNetworkMap, inboundAssignmentListener),
            partitionAssignmentListener = inboundAssignmentListener
        )
        inboundMessageSubscription.start()
        resources.keep(inboundMessageSubscription)
        //We complete the future inside inboundAssignmentListener.
        return future
    }

    @VisibleForTesting
    internal fun createOutboundResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val outboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Schema.P2P_OUT_TOPIC, instanceId),
            outboundMessageProcessor,
            partitionAssignmentListener = null
        )
        outboundMessageSubscription.start()
        resources.keep(outboundMessageSubscription)
        val outboundReady = CompletableFuture<Unit>()
        outboundReady.complete(Unit)
        return outboundReady
    }

    private val commonChildren = setOf(linkManagerNetworkMap.dominoTile, linkManagerCryptoService.dominoTile,
        linkManagerHostingMap.dominoTile)
    private val inboundDominoTile = DominoTile(
        "InboundProcessor",
        lifecycleCoordinatorFactory,
        ::createInboundResources,
        children = commonChildren
    )
    private val outboundDominoTile = DominoTile(
        "OutboundProcessor",
        lifecycleCoordinatorFactory,
        ::createOutboundResources,
        children = setOf(inboundDominoTile, messagesPendingSession.dominoTile) + commonChildren)

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        children = setOf(inboundDominoTile, outboundDominoTile, deliveryTracker.dominoTile))

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
            return if (linkManagerHostingMap.isHostedLocally(message.header.destination.toHoldingIdentity())) {
                listOf(Record(P2P_IN_TOPIC, generateKey(), AppMessage(message)))
            } else {
                val linkOutMessage = linkOutFromUnauthenticatedMessage(message, networkMap)
                listOf(Record(Schema.LINK_OUT_TOPIC, generateKey(), linkOutMessage))
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
            val isHostedLocally = linkManagerHostingMap.isHostedLocally(messageAndKey.message.header.destination.toHoldingIdentity())
            return if (isHostedLocally) {
                mutableListOf(Record(P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)))
            } else {
                when (val state = sessionManager.processOutboundMessage(messageAndKey)) {
                    is SessionState.NewSessionNeeded -> recordsForNewSession(state)
                    is SessionState.SessionEstablished -> recordsForSessionEstablished(state, messageAndKey)
                    is SessionState.SessionAlreadyPending, SessionState.CannotEstablishSession -> emptyList()
                }
            } + if (!isReplay) recordsForMarkers(messageAndKey, isHostedLocally) else emptyList()
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
            return Record(Schema.P2P_OUT_MARKERS, messageId, marker)
        }

        private fun recordForLMReceivedMarker(messageId: String): Record<String, AppMessageMarker> {
            val marker = AppMessageMarker(LinkManagerReceivedMarker(), Instant.now().toEpochMilli())
            return Record(Schema.P2P_OUT_MARKERS, messageId, marker)
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
                when(val payload = message.payload) {
                    is InitiatorHelloMessage -> {
                        val partitionsAssigned = inboundAssignmentListener.getCurrentlyAssignedPartitions(Schema.LINK_IN_TOPIC).toList()
                        listOf(
                            Record(Schema.LINK_OUT_TOPIC, generateKey(), response),
                            Record(Schema.SESSION_OUT_PARTITIONS, payload.header.sessionId, SessionPartitions(partitionsAssigned))
                        )
                    }
                    else -> {
                        listOf(Record(Schema.LINK_OUT_TOPIC, generateKey(), response))
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
                    messages.addAll(processLinkManagerPayload(sessionDirection.key, sessionDirection.session, sessionId, message))
                }
                is SessionDirection.Outbound -> {
                    extractPayload(sessionDirection.session, sessionId, message, MessageAck::fromByteBuffer)?.let {
                        when (val ack = it.ack) {
                            is AuthenticatedMessageAck -> {
                                sessionManager.messageAcknowledged(sessionId)
                                messages.add(makeMarkerForAckMessage(ack))
                            }
                            is HeartbeatMessageAck -> sessionManager.messageAcknowledged(sessionId)
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

        private fun processLinkManagerPayload(
            sessionKey: SessionKey,
            session: Session,
            sessionId: String,
            message: DataMessage
        ): MutableList<Record<*, *>> {
            val messages = mutableListOf<Record<*, *>>()
            extractPayload(session, sessionId, message, DataMessagePayload::fromByteBuffer)?.let {
                when (val innerMessage = it.message) {
                    is HeartbeatMessage -> {
                        makeAckMessageForHeartbeatMessage(sessionKey, session)?.let { ack -> messages.add(ack) }
                    }
                    is AuthenticatedMessageAndKey -> {
                        messages.add(Record(P2P_IN_TOPIC, innerMessage.key, AppMessage(innerMessage.message)))
                        makeAckMessageForFlowMessage(innerMessage.message, session)?.let { ack -> messages.add(ack) }
                        sessionManager.inboundSessionEstablished(sessionId)
                    }
                    else -> logger.warn("The message was discarded.")
                }
            }
            return messages
        }

        private fun makeAckMessageForHeartbeatMessage(
            key: SessionKey,
            session: Session
        ): Record<String, LinkOutMessage>? {
            val ackDest = key.responderId.toHoldingIdentity()
            val ackSource = key.ourId.toHoldingIdentity()
            val ack = linkOutMessageFromAck(
                MessageAck(HeartbeatMessageAck()),
                ackSource,
                ackDest,
                session,
                networkMap
            ) ?: return null
            return Record(
                Schema.LINK_OUT_TOPIC,
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
                Schema.LINK_OUT_TOPIC,
                generateKey(),
                ack
            )
        }

        private fun makeMarkerForAckMessage(message: AuthenticatedMessageAck): Record<String, AppMessageMarker> {
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
        fun queueMessage(message: AuthenticatedMessageAndKey, key: SessionKey)
        fun sessionNegotiatedCallback(
            sessionManager: SessionManager,
            key: SessionKey,
            session: Session,
            networkMap: LinkManagerNetworkMap,
        )
        fun destroyQueue(key: SessionKey)
        fun destroyAllQueues()
        val dominoTile: DominoTile
    }

    class PendingSessionMessageQueuesImpl(
        publisherFactory: PublisherFactory,
        coordinatorFactory: LifecycleCoordinatorFactory,
        configuration: SmartConfig,
        instanceId: Int
    ): PendingSessionMessageQueues, LifecycleWithDominoTile {
        private val queuedMessagesPendingSession = HashMap<SessionKey, Queue<AuthenticatedMessageAndKey>>()
        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
            PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID, instanceId),
            configuration
        )
        override val dominoTile = publisher.dominoTile

        /**
         * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
         * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
        */
        override fun queueMessage(message: AuthenticatedMessageAndKey, key: SessionKey) {
            val oldQueue = queuedMessagesPendingSession.putIfAbsent(key, LinkedList())
            if (oldQueue != null) {
                oldQueue.add(message)
            } else {
                queuedMessagesPendingSession[key]?.add(message)
            }
        }

        /**
         * Publish all the queued [FlowMessage]s to the P2P_OUT_TOPIC.
         */
        override fun sessionNegotiatedCallback(
            sessionManager: SessionManager,
            key: SessionKey,
            session: Session,
            networkMap: LinkManagerNetworkMap,
        ) {
            publisher.dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("sessionNegotiatedCallback was called before the PendingSessionMessageQueues was started.")
                }
                val queuedMessages = queuedMessagesPendingSession[key] ?: return@withLifecycleLock
                val records = mutableListOf<Record<String, *>>()
                while (queuedMessages.isNotEmpty()) {
                    val message = queuedMessages.poll()
                    records.addAll(recordsForSessionEstablished(sessionManager, networkMap, session, message))
                }
                publisher.publish(records)
            }
        }

        override fun destroyQueue(key: SessionKey) {
            queuedMessagesPendingSession.remove(key)
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
        records.add(Record(Schema.LINK_OUT_TOPIC, key, it))
    }
    return records
}
