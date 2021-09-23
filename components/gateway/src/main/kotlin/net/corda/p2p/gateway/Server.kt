package net.corda.p2p.gateway

import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.domino.LifecycleWithCoordinator
import net.corda.p2p.gateway.domino.LifecycleWithCoordinatorAndResources
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl

internal class Server(
    parent: LifecycleWithCoordinator,
    private val configurationService: GatewayConfigurationService,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory
) : LifecycleWithCoordinatorAndResources(
    parent
) {
    private val httpServer = HttpServer(this, configurationService)
    private val sessionPartitionMapper = SessionPartitionMapperImpl(this, subscriptionFactory)
    private val inboundMessageProcessor = InboundMessageHandler(this, publisherFactory, httpServer, sessionPartitionMapper)

    override fun onStatusUp() {
        if (configurationService.state != State.Up) {
            return
        }
        if (httpServer.state != State.Up) {
            httpServer.start()
            return
        }
        if (sessionPartitionMapper.state != State.Up) {
            sessionPartitionMapper.start()
            return
        }
        if (inboundMessageProcessor.state != State.Up) {
            inboundMessageProcessor.start()
            return
        }
        state = State.Up
    }

    override fun onStatusDown() {
        stop()
    }

    init {
        // YIFT: Why can't I follow all of them together?!
        listOf(
            configurationService,
            httpServer,
            sessionPartitionMapper,
            inboundMessageProcessor
        )
            .map {
                followStatusChanges(it)
            }.forEach {
                executeBeforeClose(it::close)
            }
    }

    override fun resumeSequence() {
        listOf(
            httpServer,
            sessionPartitionMapper,
            inboundMessageProcessor
        ).forEach {
            executeBeforePause(it::stop)
        }

        onStatusUp()
    }
}
