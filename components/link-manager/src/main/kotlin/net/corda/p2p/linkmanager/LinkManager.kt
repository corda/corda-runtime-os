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
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toSessionNetworkMapPeer
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.createLinkOutMessageFromFlowMessage
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.processMessageWithSession
import net.corda.p2p.linkmanager.sessions.SessionManager
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class LinkManager(@Reference(service = SubscriptionFactory::class)
                  private val subscriptionFactory: SubscriptionFactory,
                  @Reference(service = PublisherFactory::class)
                  private val publisherFactory: PublisherFactory,
                  linkManagerNetworkMap: LinkManagerNetworkMap,
                  maxMessageSize: Int,
                  protocolModes: Set<ProtocolMode>)
: LifeCycle {

    companion object {
        const val P2P_OUT_TOPIC = "p2p.out"
        const val P2P_IN_TOPIC = "p2p.in"
        const val LINK_OUT_TOPIC = "link.out"
        const val LINK_IN_TOPIC = "link.in"
        const val P2P_OUT_MARKERS = "p2p.out.markers"
        const val KEY = "key"

        const val LINK_MANAGER_PUBLISHER_CLIENT_ID = "linkmanager"
        const val INBOUND_MESSAGE_FORWARDER_GROUP = "inbound_message_forwarder_group"
        const val OUTBOUND_MESSAGE_FORWARDER_GROUP = "outbound_message_forwarder_group"

        fun getSessionKeyFromMessage(message: FlowMessage): SessionManager.SessionKey {
            return SessionManager.SessionKey(message.header.source.groupId, message.header.destination.toSessionNetworkMapPeer())
        }
    }

    private var outboundMessageForwarder: Subscription<String, FlowMessage>
    private var inboundMessageForwarder: Subscription<String, LinkInMessage>
    private var messagesPendingSession = PendingSessionsMessageQueues(publisherFactory)
    private var sessionManager: SessionManager = SessionManager(
        protocolModes,
        linkManagerNetworkMap,
        maxMessageSize,
        messagesPendingSession::sessionNegotiatedCallback
    )

    class OutboundMessageForwarder(
        private val sessionManager: SessionManager,
        private val pendingSessionsMessageQueues: PendingSessionsMessageQueues,
        private val networkMap: LinkManagerNetworkMap
    ) :
        EventLogProcessor<String, FlowMessage> {

        override val keyClass = String::class.java
        override val valueClass = FlowMessage::class.java

        //We use an EventLogProcessor here instead of a DurableProcessor, as During [CORE-1286] we will use the
        //offset and partition.
        override fun onNext(events: List<EventLogRecord<String, FlowMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, LinkOutMessage>>()
            for (event in events) {
                val sessionKey = getSessionKeyFromMessage(event.value)
                val session = sessionManager.getInitiatorSession(sessionKey)
                val message = if (session == null) {
                    val newSessionNeeded = pendingSessionsMessageQueues.queueMessage(event.value)
                    if (newSessionNeeded) {
                        sessionManager.beginSessionNegotiation(sessionKey)
                    } else {
                        null
                    }
                } else {
                    createLinkOutMessageFromFlowMessage(event.value, session, networkMap)
                }
                if (message != null) {
                    records.add(Record(P2P_OUT_TOPIC, KEY, message))
                }
            }
            return records
        }
    }

    class InboundMessageForwarder(private val sessionManager: SessionManager) :
        EventLogProcessor<String, LinkInMessage> {

        private val logger = LoggerFactory.getLogger(this::class.java.name)

        override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, *>>()
            for (event in events) {
                if (event.value.payload is AuthenticatedDataMessage || event.value.payload is AuthenticatedEncryptedDataMessage) {
                    val sessionId = getSessionFromDataMessage(event.value)
                    val session =  sessionManager.getResponderSession(sessionId)
                    if (session == null) {
                        logger.warn("Received message with SessionId = $sessionId for which there is no active session.")
                    } else {
                        val flowMessage = processMessageWithSession(session, sessionId, event.value)
                        if (flowMessage != null) {
                            records.add(Record(LINK_OUT_TOPIC, KEY, flowMessage))
                        }
                    }
                } else {
                    val gatewayMessage = sessionManager.processSessionMessage(event.value)
                    records.add(Record(P2P_OUT_TOPIC, KEY, gatewayMessage))
                }
            }
            return records
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

    class PendingSessionsMessageQueues(publisherFactory: PublisherFactory) {
        private val queuedMessagesPendingSession = ConcurrentHashMap<SessionManager.SessionKey, ConcurrentLinkedQueue<FlowMessage>>()
        private val config = PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID, null)
        private val publisher = publisherFactory.createPublisher(config, emptyMap())

        /**
         * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
         * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
         * Returns [true] if we need to start session negotiation and [false] if we don't (if the session is pending).
        */
        fun queueMessage(message: FlowMessage): Boolean {
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
        fun sessionNegotiatedCallback(key: SessionManager.SessionKey, session: Session, networkMap: LinkManagerNetworkMap) {
            val queuedMessages = queuedMessagesPendingSession[key] ?: return
            val records = mutableListOf<Record<String, LinkOutMessage>>()
            for (i in 0 until queuedMessages.size) {
                val message = queuedMessages.remove() ?: break
                val authenticatedDataMessage = createLinkOutMessageFromFlowMessage(message, session, networkMap)
                records.add(Record(P2P_OUT_TOPIC, KEY, authenticatedDataMessage))
            }
            publisher.publish(records)
        }
    }

    init {
        val outboundMessageForwarderConfig = SubscriptionConfig(OUTBOUND_MESSAGE_FORWARDER_GROUP, LINK_OUT_TOPIC)
        outboundMessageForwarder = subscriptionFactory.createEventLogSubscription(
            outboundMessageForwarderConfig,
            OutboundMessageForwarder(sessionManager, messagesPendingSession, linkManagerNetworkMap),
            mapOf(),
            null
        )
        val inboundMessageForwarderConfig = SubscriptionConfig(INBOUND_MESSAGE_FORWARDER_GROUP, LINK_IN_TOPIC)
        inboundMessageForwarder = subscriptionFactory.createEventLogSubscription(
            inboundMessageForwarderConfig,
            InboundMessageForwarder(sessionManager),
            mapOf(),
            null
        )
    }

    override fun start() {
        outboundMessageForwarder.start()
        inboundMessageForwarder.start()
    }

    override fun stop() {
        outboundMessageForwarder.stop()
        inboundMessageForwarder.stop()
    }

    override val isRunning: Boolean
        get() = outboundMessageForwarder.isRunning && inboundMessageForwarder.isRunning
}