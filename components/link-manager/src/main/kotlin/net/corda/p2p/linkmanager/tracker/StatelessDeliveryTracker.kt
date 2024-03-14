package net.corda.p2p.linkmanager.tracker

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC

internal class StatelessDeliveryTracker(
    private val commonComponents: CommonComponents,
    messagingConfiguration: SmartConfig,
    outboundMessageProcessor: OutboundMessageProcessor,
) : LifecycleWithDominoTile {
    private val subscriptionConfig = SubscriptionConfig(
        groupName = "stateless-delivery-tracker",
        eventTopic = P2P_OUT_TOPIC,
    )
    private val config = DeliveryTrackerConfiguration(
        configurationReaderService = commonComponents.configurationReaderService,
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
    )

    private val publisher = PublisherWithDominoLogic(
        publisherFactory = commonComponents.publisherFactory,
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        publisherConfig = PublisherConfig(
            transactional = true,
            clientId = "DeliveryTracker",
        ),
        messagingConfiguration = messagingConfiguration,
    )
    private val partitionsStates = PartitionsStates(
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        stateManager = commonComponents.stateManager,
        config = config,
        clock = commonComponents.clock,
    )
    private val p2pOutSubscription = {
        commonComponents.subscriptionFactory.createEventLogSubscription(
            subscriptionConfig = subscriptionConfig,
            processor = DeliveryTrackerProcessor(
                outboundMessageProcessor,
                partitionsStates,
                publisher,
            ),
            messagingConfig = messagingConfiguration,
            partitionAssignmentListener = null,
        )
    }

    override val dominoTile = SubscriptionDominoTile(
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        subscriptionGenerator = p2pOutSubscription,
        subscriptionConfig = subscriptionConfig,
        dependentChildren = listOf(
            partitionsStates.dominoTile.coordinatorName,
            config.dominoTile.coordinatorName,
            publisher.dominoTile.coordinatorName,
        ),
        managedChildren = listOf(
            partitionsStates.dominoTile.toNamedLifecycle(),
            config.dominoTile.toNamedLifecycle(),
            publisher.dominoTile.toNamedLifecycle(),
        ),
    )
}
