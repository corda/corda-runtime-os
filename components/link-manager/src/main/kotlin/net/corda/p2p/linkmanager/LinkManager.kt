package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.FlowMessage
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toSessionNetworkMapPeer
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.createLinkOutMessageFromFlowMessage
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.convertToFlowMessage
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.schema.Topics
import net.corda.v5.base.annotations.VisibleForTesting
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class LinkManager(@Reference(service = SubscriptionFactory::class)
                  private val subscriptionFactory: SubscriptionFactory,
                  @Reference(service = PublisherFactory::class)
                  private val publisherFactory: PublisherFactory,
                  linkManagerNetworkMap: LinkManagerNetworkMap,
                  linkManagerCryptoService: LinkManagerCryptoService,
                  config: LinkManagerConfig
)
: LifeCycle {

    companion object {
        const val KEY = "key"

        const val LINK_MANAGER_PUBLISHER_CLIENT_ID = "linkmanager"
        const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
        const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"

        fun getSessionKeyFromMessage(message: FlowMessage): SessionManagerImpl.SessionKey {
            return SessionManagerImpl.SessionKey(message.header.source.groupId, message.header.destination.toSessionNetworkMapPeer())
        }
    }

    private var outboundMessageSubscription: Subscription<String, FlowMessage>
    private var inboundMessageSubscription: Subscription<String, LinkInMessage>
    private var messagesPendingSession = PendingSessionsMessageQueuesImpl(publisherFactory)
    private var sessionManager: SessionManager = SessionManagerImpl(
        config.protocolModes,
        linkManagerNetworkMap,
        linkManagerCryptoService,
        config.maxMessageSize,
        messagesPendingSession::sessionNegotiatedCallback
    )

    class OutboundMessageProcessor(
        private val sessionManager: SessionManager,
        private val pendingSessionsMessageQueues: PendingSessionsMessageQueues,
        private val networkMap: LinkManagerNetworkMap
    ) : EventLogProcessor<String, FlowMessage> {

        override val keyClass = String::class.java
        override val valueClass = FlowMessage::class.java

        //We use an EventLogProcessor here instead of a DurableProcessor, as During [CORE-1286] we will use the
        //offset and partition.
        override fun onNext(events: List<EventLogRecord<String, FlowMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, LinkOutMessage>>()
            for (event in events) {
                processEvent(event)?.let { records.add(Record(Topics.LINK_OUT_TOPIC, KEY, it)) }
            }
            return records
        }

        private fun processEvent(event: EventLogRecord<String, FlowMessage>): LinkOutMessage? {
            val sessionKey = getSessionKeyFromMessage(event.value)
            val session = sessionManager.getInitiatorSession(sessionKey)
            if (session == null) {
                val newSessionNeeded = pendingSessionsMessageQueues.queueMessage(event.value)
                if (newSessionNeeded) {
                    return sessionManager.getSessionInitMessage(sessionKey)
                }
            } else {
                return createLinkOutMessageFromFlowMessage(event.value, session, networkMap)
            }
            return null
        }
    }

    class InboundMessageProcessor(private val sessionManager: SessionManager) :
        EventLogProcessor<String, LinkInMessage> {

        private var logger = LoggerFactory.getLogger(this::class.java.name)

        @VisibleForTesting
        fun setLogger(newLogger: Logger) {
            logger = newLogger
        }

        override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, *>>()
            for (event in events) {
                if (event.value.payload is AuthenticatedDataMessage || event.value.payload is AuthenticatedEncryptedDataMessage) {
                    extractAndCheckMessage(event.value)?.let { records.add(Record(Topics.P2P_IN_TOPIC, KEY, it)) }
                } else {
                    sessionManager.processSessionMessage(event.value)?.let { records.add(Record(Topics.LINK_OUT_TOPIC, KEY, it)) }
                }
            }
            return records
        }

        /**
         * This function extracts (decrypts if necessary) the payload from the message.
         * It checks we have negotiated a session with the sender and authenticates the message.
         */
        private fun extractAndCheckMessage(message: LinkInMessage): FlowMessage? {
            val sessionId = getSessionFromDataMessage(message)
            val session =  sessionManager.getResponderSession(sessionId)
            if (session == null) {
                logger.warn("Received message with SessionId = $sessionId for which there is no active session." +
                        " The message was discarded.")
            } else {
                return convertToFlowMessage(session, sessionId, message)
            }
            return null
        }

        private fun getSessionFromDataMessage(message: LinkInMessage): String {
            return when (val payload = message.payload) {
                is AuthenticatedDataMessage -> payload.header.sessionId
                is AuthenticatedEncryptedDataMessage -> payload.header.sessionId
                else -> throw IllegalArgumentException("Message must be either ${AuthenticatedDataMessage::class.java} " +
                        "or ${AuthenticatedEncryptedDataMessage::class.java}")
            }
        }

        override val keyClass = String::class.java
        override val valueClass = LinkInMessage::class.java
    }

    interface PendingSessionsMessageQueues {
        fun queueMessage(message: FlowMessage): Boolean
        fun sessionNegotiatedCallback(key: SessionManagerImpl.SessionKey, session: Session, networkMap: LinkManagerNetworkMap)
    }

    class PendingSessionsMessageQueuesImpl(publisherFactory: PublisherFactory): PendingSessionsMessageQueues {
        private val queuedMessagesPendingSession = ConcurrentHashMap<SessionManagerImpl.SessionKey, ConcurrentLinkedQueue<FlowMessage>>()
        private val config = PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID, null)
        private val publisher = publisherFactory.createPublisher(config)

        /**
         * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
         * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
         * Returns [true] if we need to start session negotiation and [false] if we don't (if the session is pending).
        */
        override fun queueMessage(message: FlowMessage): Boolean {
            val key = getSessionKeyFromMessage(message)
            val newQueue = ConcurrentLinkedQueue<FlowMessage>()
            newQueue.add(message)
            val oldQueue = queuedMessagesPendingSession.putIfAbsent(key, newQueue)
            oldQueue?.add(message)
            return oldQueue == null
        }

        /**
         * Publish all the queued [FlowMessage]s to the P2P_OUT_TOPIC.
         */
        override fun sessionNegotiatedCallback(key: SessionManagerImpl.SessionKey, session: Session, networkMap: LinkManagerNetworkMap) {
            val queuedMessages = queuedMessagesPendingSession[key] ?: return
            val records = mutableListOf<Record<String, LinkOutMessage>>()
            while (queuedMessages.isNotEmpty()) {
                val message = queuedMessages.poll()
                val authenticatedDataMessage = createLinkOutMessageFromFlowMessage(message, session, networkMap)
                records.add(Record(Topics.P2P_OUT_TOPIC, KEY, authenticatedDataMessage))
            }
            publisher.publish(records)
        }
    }

    init {
        val outboundMessageSubscriptionConfig = SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Topics.P2P_OUT_TOPIC)
        outboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            outboundMessageSubscriptionConfig,
            OutboundMessageProcessor(sessionManager, messagesPendingSession, linkManagerNetworkMap),
            partitionAssignmentListener = null
        )
        val inboundMessageSubscriptionConfig = SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Topics.LINK_IN_TOPIC)
        inboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
            inboundMessageSubscriptionConfig,
            InboundMessageProcessor(sessionManager),
            partitionAssignmentListener = null
        )
    }

    override fun start() {
        outboundMessageSubscription.start()
        inboundMessageSubscription.start()
    }

    override fun stop() {
        outboundMessageSubscription.stop()
        inboundMessageSubscription.stop()
    }

    override val isRunning: Boolean
        get() = outboundMessageSubscription.isRunning && inboundMessageSubscription.isRunning
}
