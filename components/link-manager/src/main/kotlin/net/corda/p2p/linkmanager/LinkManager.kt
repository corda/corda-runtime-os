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
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.SessionPartitions
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses.DataMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.extractPayload
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromAck
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromFlowMessageAndKey
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionDirection
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.SessionKey
import net.corda.p2p.payload.FlowMessage
import net.corda.p2p.schema.Schema
import net.corda.p2p.markers.FlowMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.payload.FlowMessageAndKey
import net.corda.p2p.payload.MessageAck
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.nio.ByteBuffer
import kotlin.concurrent.withLock

class LinkManager(@Reference(service = SubscriptionFactory::class)
                  subscriptionFactory: SubscriptionFactory,
                  @Reference(service = PublisherFactory::class)
                  publisherFactory: PublisherFactory,
                  linkManagerNetworkMap: LinkManagerNetworkMap,
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

    private val outboundMessageSubscription: Subscription<ByteBuffer, FlowMessage>
    private val inboundMessageSubscription: Subscription<String, LinkInMessage>
    private val inboundAssignmentListener = InboundAssignmentListener()

    private val messagesPendingSession = PendingSessionMessageQueuesImpl(publisherFactory)
    private val sessionManager: SessionManager = SessionManagerImpl(
        config.protocolModes,
        linkManagerNetworkMap,
        linkManagerCryptoService,
        config.maxMessageSize,
        messagesPendingSession
    )

    init {
        val outboundMessageSubscriptionConfig = SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Schema.P2P_OUT_TOPIC)
        outboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            outboundMessageSubscriptionConfig,
            OutboundMessageProcessor(sessionManager, linkManagerNetworkMap, inboundAssignmentListener),
            partitionAssignmentListener = null
        )
        val inboundMessageSubscriptionConfig = SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Schema.LINK_IN_TOPIC)
        inboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            inboundMessageSubscriptionConfig,
            InboundMessageProcessor(sessionManager, linkManagerNetworkMap),
            partitionAssignmentListener = inboundAssignmentListener
        )
    }

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                inboundMessageSubscription.start()
                /*We must wait for partitions to be assigned to the inbound subscription before we can start the outbound
                *subscription otherwise the gateway won't know which partition to route message back to.*/
                inboundAssignmentListener.awaitFirstAssignment()
                outboundMessageSubscription.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
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
        private val networkMap: LinkManagerNetworkMap,
        private val inboundAssignmentListener: InboundAssignmentListener
    ) : EventLogProcessor<ByteBuffer, FlowMessage> {

        override val keyClass = ByteBuffer::class.java
        override val valueClass = FlowMessage::class.java
        private var logger = LoggerFactory.getLogger(this::class.java.name)

        override fun onNext(events: List<EventLogRecord<ByteBuffer, FlowMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, *>>()
            for (event in events) {
                records += processEvent(event)
            }
            return records
        }

        private fun processEvent(event: EventLogRecord<ByteBuffer, FlowMessage>): List<Record<String, *>> {
            val message = event.value
            if (message == null) {
                logger.error("Received null message. The message was discarded.")
                return emptyList()
            }
            val flowMessageAndKey = FlowMessageAndKey(message, event.key)

            return when (val state = sessionManager.processOutboundFlowMessage(flowMessageAndKey)) {
                is SessionState.NewSessionNeeded -> recordsForNewSession(state)
                is SessionState.SessionEstablished -> recordsForSessionEstablished(state, flowMessageAndKey)
                is SessionState.SessionAlreadyPending, SessionState.CannotEstablishSession -> emptyList()
            } + recordForMarker(event, message.header.messageId)
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
            flowMessageAndKey: FlowMessageAndKey
        ): List<Record<String, *>> {
            val records = mutableListOf<Record<String, *>>()
            val message = linkOutMessageFromFlowMessageAndKey(flowMessageAndKey, state.session, networkMap) ?: return emptyList()
            records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), message))
            return records
        }

        private fun recordForMarker(event: EventLogRecord<ByteBuffer, FlowMessage>, messageId: String): Record<String, FlowMessageMarker> {
            val marker = FlowMessageMarker(LinkManagerSentMarker(event.partition.toLong(), event.offset))
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
                    is InitiatorHandshakeMessage, is Step2Message -> processSessionMessage(message)
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
                    extractPayload(sessionDirection.session, sessionId, message, FlowMessageAndKey::fromByteBuffer)?.let {
                        messages.add(Record(Schema.P2P_IN_TOPIC, it.key, it.flowMessage))
                        makeAckMessageForFlowMessage(it.flowMessage, sessionDirection.session)?.let { ack -> messages.add(ack) }
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

        private fun makeAckMessageForFlowMessage(message: FlowMessage, session: Session): Record<String, LinkOutMessage>? {
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

        private fun makeMarkerForAckMessage(message: MessageAck): Record<String, FlowMessageMarker> {
            return Record(Schema.P2P_OUT_MARKERS, message.messageId, FlowMessageMarker(LinkManagerReceivedMarker()))
        }

        override val keyClass = String::class.java
        override val valueClass = LinkInMessage::class.java
    }

    interface PendingSessionMessageQueues {
        fun queueMessage(message: FlowMessageAndKey, key: SessionKey): Boolean
        fun sessionNegotiatedCallback(key: SessionKey, session: Session, networkMap: LinkManagerNetworkMap)
    }

    class PendingSessionMessageQueuesImpl(publisherFactory: PublisherFactory): PendingSessionMessageQueues {
        private val queuedMessagesPendingSession = HashMap<SessionKey, Queue<FlowMessageAndKey>>()
        private val config = PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID, null)
        private val publisher = publisherFactory.createPublisher(config)

        /**
         * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
         * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
         * Returns [true] if we need to start session negotiation and [false] if we don't (if the session is pending).
        */
        override fun queueMessage(message: FlowMessageAndKey, key: SessionKey): Boolean {
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
