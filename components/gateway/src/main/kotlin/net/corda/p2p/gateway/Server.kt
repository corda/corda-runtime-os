package net.corda.p2p.gateway

import net.corda.lifecycle.LifecycleStatus
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
    private fun startNextThing() {
        if (configurationService.status != LifecycleStatus.UP) {
            return
        }
        if (httpServer.status != LifecycleStatus.UP) {
            httpServer.start()
            return
        }
        if (sessionPartitionMapper.status != LifecycleStatus.UP) {
            sessionPartitionMapper.start()
            return
        }
        if (inboundMessageProcessor.status != LifecycleStatus.UP) {
            inboundMessageProcessor.start()
            return
        }
        status = LifecycleStatus.UP
    }

    override fun onStatusChange(newStatus: LifecycleStatus) {
        if (newStatus == LifecycleStatus.DOWN) {
            stop()
        } else {
            startNextThing()
        }

        super.onStatusChange(newStatus)
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

    override fun onStart() {
        listOf(
            httpServer,
            sessionPartitionMapper,
            inboundMessageProcessor
        ).forEach {
            executeBeforeStop(it::stop)
        }

        startNextThing()
    }
}
