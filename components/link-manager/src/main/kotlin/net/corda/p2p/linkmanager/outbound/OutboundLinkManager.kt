package net.corda.p2p.linkmanager.outbound

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.p2p.linkmanager.sessions.SessionManagerCommonComponents
import net.corda.p2p.linkmanager.tracker.StatefulDeliveryTracker
import net.corda.schema.Schemas
import net.corda.utilities.flags.Features

internal class OutboundLinkManager(
    commonComponents: CommonComponents,
    sessionComponents: SessionManagerCommonComponents,
    messagingConfiguration: SmartConfig,
    features: Features = Features()
) : LifecycleWithDominoTile {
    companion object {
        private const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"
    }
    private val outboundMessageProcessor = OutboundMessageProcessor(
        sessionComponents.sessionManager,
        commonComponents.linkManagerHostingMap,
        commonComponents.groupPolicyProvider,
        commonComponents.membershipGroupReaderProvider,
        commonComponents.messagesPendingSession,
        commonComponents.clock,
        commonComponents.messageConverter,
    )
    private val deliveryTracker = DeliveryTracker(
        commonComponents,
        messagingConfiguration,
        sessionComponents.sessionManager,
    ) { outboundMessageProcessor.processReplayedAuthenticatedMessage(it) }

    private val subscriptionConfig = SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.P2P_OUT_TOPIC)

    private val outboundMessageSubscription = {
        commonComponents.subscriptionFactory.createEventLogSubscription(
            subscriptionConfig,
            outboundMessageProcessor,
            messagingConfiguration,
            partitionAssignmentListener = null
        )
    }

    override val dominoTile = if (features.enableP2PStatefulDeliveryTracker) {
        val publisher = PublisherWithDominoLogic(
            publisherFactory = commonComponents.publisherFactory,
            coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
            publisherConfig = PublisherConfig(
                transactional = true,
                clientId = "DeliveryTracker",
            ),
            messagingConfiguration = messagingConfiguration,
        )
        val statefulDeliveryTracker = StatefulDeliveryTracker(
            commonComponents = commonComponents,
            publisher = publisher,
            messagingConfiguration = messagingConfiguration,
            outboundMessageProcessor = outboundMessageProcessor,
        )
        ComplexDominoTile(
            OUTBOUND_MESSAGE_PROCESSOR_GROUP,
            coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
            dependentChildren = listOf(
                statefulDeliveryTracker.dominoTile.coordinatorName,
                publisher.dominoTile.coordinatorName,
            ),
            managedChildren = listOf(
                statefulDeliveryTracker.dominoTile.toNamedLifecycle(),
                publisher.dominoTile.toNamedLifecycle(),
            ),
        )
    } else {
        SubscriptionDominoTile(
            commonComponents.lifecycleCoordinatorFactory,
            outboundMessageSubscription,
            subscriptionConfig,
            dependentChildren = listOf(
                deliveryTracker.dominoTile.coordinatorName,
                commonComponents.dominoTile.coordinatorName,
                sessionComponents.dominoTile.coordinatorName,
            ),
            managedChildren = setOf(deliveryTracker.dominoTile.toNamedLifecycle())
        )
    }
}
