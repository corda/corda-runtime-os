package net.corda.p2p.linkmanager.tracker

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.delivery.ReplayScheduler
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.schema.Schemas.P2P.LINK_ACK_IN_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import java.util.UUID

internal class StatefulDeliveryTracker(
    private val commonComponents: CommonComponents,
    messagingConfiguration: SmartConfig,
    publisher: PublisherWithDominoLogic,
    outboundMessageProcessor: OutboundMessageProcessor,
) : LifecycleWithDominoTile {
    private val subscriptionConfig = SubscriptionConfig(
        groupName = "stateless-delivery-tracker",
        eventTopic = P2P_OUT_TOPIC,
    )
    private val ackSubscriptionConfig = SubscriptionConfig(
        groupName = "stateless-delivery-tracker-acks-${UUID.randomUUID()}",
        eventTopic = LINK_ACK_IN_TOPIC,
    )
    private val config = DeliveryTrackerConfiguration(
        configurationReaderService = commonComponents.configurationReaderService,
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
    )
    private val cache = DataMessageCache(
        commonComponents,
        config,
        ::onOffsetsToReadFromChanged,
    )

    private val replayer = MessageReplayer(
        publisher = publisher,
        outboundMessageProcessor = outboundMessageProcessor,
        cache = cache,
    )

    private val replayScheduler = ReplayScheduler<SessionManager.Counterparties, String>(
        commonComponents = commonComponents,
        limitTotalReplays = true,
        replayer,
    )

    private val partitionsStates = PartitionsStates(
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        stateManager = commonComponents.stateManager,
        config = config,
        clock = commonComponents.clock,
        replayScheduler,
    )
    private val handler = MessagesHandler(
        partitionsStates = partitionsStates,
        cache = cache,
    )

    private val ackSubscription = {
        commonComponents.subscriptionFactory.createPubSubSubscription(
            subscriptionConfig = ackSubscriptionConfig,
            processor = AckProcessor(
                partitionsStates = partitionsStates,
                cache = cache,
                replayScheduler = replayScheduler,
            ),
            messagingConfig = messagingConfiguration,
        )
    }

    private val p2pOutSubscription = {
        commonComponents.subscriptionFactory.createEventSourceSubscription(
            subscriptionConfig = subscriptionConfig,
            processor = DeliveryTrackerProcessor(
                outboundMessageProcessor,
                handler,
                publisher,
            ),
            messagingConfig = messagingConfiguration,
            partitionAssignmentListener = DeliveryTrackerPartitionAssignmentListener(
                partitionsStates,
            ),
            consumerOffsetProvider = DeliveryTrackerOffsetProvider(
                partitionsStates,
            ),
        )
    }

    private fun onOffsetsToReadFromChanged(partitionsToLastPersistedOffset: Collection<Pair<Int, Long>>) {
        partitionsStates.offsetsToReadFromChanged(partitionsToLastPersistedOffset)
    }

    private val p2pOutSubscriptionDominoTile = SubscriptionDominoTile(
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        subscriptionGenerator = p2pOutSubscription,
        subscriptionConfig = subscriptionConfig,
        managedChildren = emptySet(),
        dependentChildren = setOf(
            partitionsStates.dominoTile.coordinatorName,
            config.dominoTile.coordinatorName,
            publisher.dominoTile.coordinatorName,
            cache.dominoTile.coordinatorName,
            replayScheduler.dominoTile.coordinatorName,
        ),
    )
    private val ackSubscriptionDominoTile = SubscriptionDominoTile(
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        subscriptionGenerator = ackSubscription,
        subscriptionConfig = subscriptionConfig,
        managedChildren = emptySet(),
        dependentChildren = setOf(
            partitionsStates.dominoTile.coordinatorName,
            config.dominoTile.coordinatorName,
            cache.dominoTile.coordinatorName,
            replayScheduler.dominoTile.coordinatorName,
            p2pOutSubscriptionDominoTile.coordinatorName,
        ),
    )

    override val dominoTile = ComplexDominoTile(
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            partitionsStates.dominoTile.coordinatorName,
            config.dominoTile.coordinatorName,
            publisher.dominoTile.coordinatorName,
            cache.dominoTile.coordinatorName,
            replayScheduler.dominoTile.coordinatorName,
            p2pOutSubscriptionDominoTile.coordinatorName,
            ackSubscriptionDominoTile.coordinatorName,
        ),
        managedChildren = listOf(
            partitionsStates.dominoTile.toNamedLifecycle(),
            config.dominoTile.toNamedLifecycle(),
            cache.dominoTile.toNamedLifecycle(),
            replayScheduler.dominoTile.toNamedLifecycle(),
            p2pOutSubscriptionDominoTile.toNamedLifecycle(),
            ackSubscriptionDominoTile.toNamedLifecycle(),
        ),
        componentName = "StatefulDeliveryTracker",
    )
}
