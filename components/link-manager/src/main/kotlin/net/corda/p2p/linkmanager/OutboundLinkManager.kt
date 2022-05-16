package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.schema.Schemas

internal class OutboundLinkManager(
    linkManager: LinkManager,
) : LifecycleWithDominoTile {
    companion object {
        private const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"
    }
    private val outboundMessageProcessor = OutboundMessageProcessor(
        linkManager.commonComponents.sessionManager,
        linkManager.linkManagerHostingMap,
        linkManager.groups,
        linkManager.members,
        linkManager.commonComponents.inboundAssignmentListener,
        linkManager.commonComponents.messagesPendingSession,
        linkManager.clock
    )
    private val deliveryTracker = DeliveryTracker(
        linkManager.lifecycleCoordinatorFactory,
        linkManager.configurationReaderService,
        linkManager.publisherFactory,
        linkManager.configuration,
        linkManager.subscriptionFactory,
        linkManager.groups,
        linkManager.members,
        linkManager.linkManagerCryptoProcessor,
        linkManager.commonComponents.sessionManager,
        clock = linkManager.clock
    ) { outboundMessageProcessor.processReplayedAuthenticatedMessage(it) }

    private val outboundMessageSubscription = linkManager.subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.P2P_OUT_TOPIC),
        outboundMessageProcessor,
        linkManager.configuration,
        partitionAssignmentListener = null
    )

    override val dominoTile = SubscriptionDominoTile(
        linkManager.lifecycleCoordinatorFactory,
        outboundMessageSubscription,
        dependentChildren = listOf(
            deliveryTracker.dominoTile,
            linkManager.commonComponents.dominoTile,
            linkManager.commonComponents.inboundAssignmentListener.dominoTile,
        ),
        managedChildren = setOf(deliveryTracker.dominoTile)
    )
}
