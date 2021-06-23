package net.corda.p2p.gateway.messaging.internal

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.gateway.Gateway.Companion.P2P_IN_TOPIC
import net.corda.p2p.gateway.Gateway.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.messaging.ReceivedMessage
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription

/**
 * This class implements a simple message processor for p2p messages received from other Gateways. Every received message will
 * result in a response being sent, after which, the message is forwarded to the upstream services, specifically Link Manager.
 */
class InboundMessageHandler(private val inboundReceiverObservable: Observable<ReceivedMessage>,
                            private val publisherFactory: PublisherFactory,
                            private val responseSender: (message: ByteArray, peer: NetworkHostAndPort) -> Unit) :
    LifeCycle {
    private var logger = LoggerFactory.getLogger(InboundMessageHandler::class.java)
    private var inboundMessageListener: Subscription? = null
    private var p2pInPublisher: Publisher? = null

    private var started = false
    override val isRunning: Boolean
        get() = started

    override fun start() {
        logger.info("Starting P2P message receiver")
        val publisherConfig = PublisherConfig(PUBLISHER_ID)
        p2pInPublisher = publisherFactory.createPublisher(publisherConfig, emptyMap())
        inboundMessageListener = inboundReceiverObservable.subscribe { msg ->
            /**
             * Add business logic for potential DH secret creation and session management
             */
            val records = mutableListOf<Record<String, String>>()
            records.add(Record(P2P_IN_TOPIC, "sessionID", String(msg.payload)))
            p2pInPublisher?.publish(records)
            responseSender("RECEIVED".toByteArray(), msg.source!!)
            msg.release()
        }
        started = true
        logger.info("Started P2P message receiver")
    }

    override fun stop() {
        started = false
        inboundMessageListener?.unsubscribe()
        inboundMessageListener = null
        p2pInPublisher?.close()
        p2pInPublisher = null
    }
}