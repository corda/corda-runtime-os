package net.corda.p2p.linkmanager

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.GatewayToLinkManagerMessage
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.linkmanager.sessions.InboundMessageRouter
import net.corda.p2p.linkmanager.sessions.SessionManagerInitiator
import net.corda.p2p.linkmanager.sessions.SessionManagerResponder
import net.corda.p2p.linkmanager.sessions.SessionNetworkMap
import org.osgi.service.component.annotations.Reference

class LinkManager(@Reference(service = SubscriptionFactory::class)
                  private val subscriptionFactory: SubscriptionFactory,
                  private val sessionNetworkMap: SessionNetworkMap,
                  private val maxMessageSize: Int) {

    companion object {
        const val P2P_OUT_TOPIC = "p2p.out"
        const val P2P_IN_TOPIC = "p2p.in"
        const val LINK_OUT_TOPIC = "link.out"
        const val LINK_IN_TOPIC = "link.in"
        const val P2P_OUT_MARKERS = "p2p.out.markers"
        const val KEY = "key"

        const val INBOUND_MESSAGE_FORWARDER_GROUP = "inbound_message_forwarder_group"
        const val OUTBOUND_MESSAGE_FORWARDER_GROUP = "outbound_message_forwarder_group"
    }

    private var outboundMessageForwarder: Subscription<String, FlowMessage>
    private var inboundMessageForwarder: Subscription<String, GatewayToLinkManagerMessage>
    private var sessionManagerInitiator: SessionManagerInitiator = SessionManagerInitiator(
        ProtocolMode.AUTHENTICATION_ONLY,
        sessionNetworkMap,
        maxMessageSize
    )
    private var sessionManagerResponder: SessionManagerResponder = SessionManagerResponder(
        ProtocolMode.AUTHENTICATION_ONLY,
        sessionNetworkMap,
        maxMessageSize
    )

    class OutboundMessageForwarder(private val sessionManager: SessionManagerInitiator) :
        EventLogProcessor<String, FlowMessage> {

        override val keyClass = String::class.java
        override val valueClass = FlowMessage::class.java

        //We use an EventLogProcessor here instead of a DurableProcessor, as During [CORE-1286] we will use the
        //offset and partition.
        override fun onNext(events: List<EventLogRecord<String, FlowMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, LinkManagerToGatewayMessage>>()
            for (event in events) {
                sessionManager.sendMessage(event.value)
                val outboundMessages = sessionManager.getQueuedOutboundMessages()
                for (message in outboundMessages) {
                    records.add(Record(P2P_OUT_TOPIC, KEY, message))
                }
            }
            return records
        }
    }

    class InboundMessageForwarder(private val router: InboundMessageRouter) :
        EventLogProcessor<String, GatewayToLinkManagerMessage> {
        override fun onNext(events: List<EventLogRecord<String, GatewayToLinkManagerMessage>>): List<Record<*, *>> {
            val records = mutableListOf<Record<String, *>>()
            for (event in events) {
                val (inboundMessages, outboundMessages) = router.routeMessage(event.value)
                for (message in inboundMessages) {
                    records.add(Record(LINK_OUT_TOPIC, KEY, message))
                }
                for (message in outboundMessages) {
                    records.add(Record(P2P_OUT_TOPIC, KEY, message))
                }
            }
            return records
        }

        override val keyClass = String::class.java
        override val valueClass = GatewayToLinkManagerMessage::class.java
    }

    init {
        val outboundMessageForwarderConfig = SubscriptionConfig(INBOUND_MESSAGE_FORWARDER_GROUP, LINK_IN_TOPIC)
        outboundMessageForwarder = subscriptionFactory.createEventLogSubscription(
            outboundMessageForwarderConfig,
            OutboundMessageForwarder(sessionManagerInitiator),
            mapOf(),
            null
        )
        val inboundMessageRouter = InboundMessageRouter(sessionManagerInitiator, sessionManagerResponder)
        val inboundMessageForwarderConfig = SubscriptionConfig(OUTBOUND_MESSAGE_FORWARDER_GROUP, LINK_IN_TOPIC)
        inboundMessageForwarder = subscriptionFactory.createEventLogSubscription(
            inboundMessageForwarderConfig,
            InboundMessageForwarder(inboundMessageRouter),
            mapOf(),
            null
        )
    }

    fun start() {
        outboundMessageForwarder.start()
        inboundMessageForwarder.start()
    }

    fun stop() {
        outboundMessageForwarder.stop()
        inboundMessageForwarder.stop()
    }
}