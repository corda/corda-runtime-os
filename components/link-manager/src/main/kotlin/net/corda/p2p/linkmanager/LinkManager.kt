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
import net.corda.p2p.SessionPartitions
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessageFromFlowMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.convertToFlowMessage
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.SessionKey
import net.corda.p2p.schema.Schema
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LinkManager(@Reference(service = SubscriptionFactory::class)
                  subscriptionFactory: SubscriptionFactory,
                  @Reference(service = PublisherFactory::class)
                  publisherFactory: PublisherFactory,
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

        fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private val outboundMessageSubscription: Subscription<String, FlowMessage>
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
            InboundMessageProcessor(sessionManager),
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
    ) : EventLogProcessor<String, FlowMessage> {

        override val keyClass = String::class.java
        override val valueClass = FlowMessage::class.java
        private var logger = LoggerFactory.getLogger(this::class.java.name)

        //We use an EventLogProcessor here instead of a DurableProcessor, as During [CORE-1286] we will use the
        //offset and partition.
        override fun onNext(events: List<EventLogRecord<String, FlowMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, *>>()
            for (event in events) {
                records += processEvent(event)
            }
            return records
        }

        private fun processEvent(event: EventLogRecord<String, FlowMessage>): MutableList<Record<String, *>> {
            val records = mutableListOf<Record<String, *>>()
            val message = event.value
            if (message == null) {
                logger.error("Received null message. The message was discarded.")
                return records
            }

            when(val state = sessionManager.processOutboundFlowMessage(message)) {
                is SessionState.NewSessionNeeded -> {
                    partitionRecordForNewSession(state.sessionId)?.let {
                        records.add(it)
                        records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), state.sessionInitMessage))
                    }
                }
                is SessionState.SessionEstablished -> {
                    val dataMessage = createLinkOutMessageFromFlowMessage(message, state.session, networkMap)
                    dataMessage?. let { records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), it)) }
                }
                //Session Pending or cannot establish session (this is logged inside the SessionManager)
                else -> {}
            }

            return records
        }

        private fun partitionRecordForNewSession(sessionId: String): Record<String, SessionPartitions>? {
            val partitions = inboundAssignmentListener.getCurrentlyAssignedPartitions(Schema.LINK_IN_TOPIC)
            if (partitions.isEmpty()) {
                logger.warn("The InboundMessageProcessor is not currently assigned to any partitions. This means there is no way to" +
                    " route a message back to the LinkManager with the Session with SessionId $sessionId in memory. The message was" +
                    " discarded.")
                return null
            }
            return Record(Schema.SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(partitions.toList()))
        }

    }

    class InboundMessageProcessor(private val sessionManager: SessionManager) :
        EventLogProcessor<String, LinkInMessage> {

        private var logger = LoggerFactory.getLogger(this::class.java.name)

        override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, *>>()
            for (event in events) {
                val message = event.value
                if (message == null) {
                    logger.error("Received null message. The message was discarded.")
                    continue
                }
                if (message.payload is AuthenticatedDataMessage || message.payload is AuthenticatedEncryptedDataMessage) {
                    extractAndCheckMessage(message)?.let { records.add(Record(Schema.P2P_IN_TOPIC, KEY, it)) }
                } else {
                    sessionManager.processSessionMessage(message)?.let { records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), it)) }
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
            val session =  sessionManager.getInboundSession(sessionId)
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

    interface PendingSessionMessageQueues {
        fun queueMessage(message: FlowMessage, key: SessionKey): Boolean
        fun sessionNegotiatedCallback(key: SessionKey, session: Session, networkMap: LinkManagerNetworkMap)
    }

    class PendingSessionMessageQueuesImpl(publisherFactory: PublisherFactory): PendingSessionMessageQueues {
        private val queuedMessagesPendingSession = HashMap<SessionKey, Queue<FlowMessage>>()
        private val config = PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID, null)
        private val publisher = publisherFactory.createPublisher(config)

        /**
         * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
         * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
         * Returns [true] if we need to start session negotiation and [false] if we don't (if the session is pending).
        */
        override fun queueMessage(message: FlowMessage, key: SessionKey): Boolean {
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
                val dataMessage = createLinkOutMessageFromFlowMessage(message, session, networkMap)
                records.add(Record(Schema.LINK_OUT_TOPIC, generateKey(), dataMessage))
            }
            publisher.publish(records)
        }
    }
}
