package net.corda.p2p.linkmanager.outbound

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.tracker.AckMessageProcessor
import net.corda.p2p.linkmanager.tracker.AckMessageProcessorImpl
import net.corda.p2p.linkmanager.tracker.DeliveryTrackerConfiguration
import net.corda.p2p.linkmanager.tracker.PartitionsStates
import net.corda.p2p.linkmanager.tracker.StatefulDeliveryTracker
import net.corda.schema.Schemas
import net.corda.utilities.flags.Features
import net.corda.utilities.time.Clock

@Suppress("LongParameterList")
internal class OutboundLinkManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    commonComponents: CommonComponents,
    linkManagerHostingMap: LinkManagerHostingMap,
    groupPolicyProvider: GroupPolicyProvider,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    configurationReaderService: ConfigurationReadService,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    clock: Clock,
    features: Features = Features()
) : LifecycleWithDominoTile {
    companion object {
        private const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"
    }

    private val deliveryTrackerConfig = if (features.enableP2PStatefulDeliveryTracker) {
        DeliveryTrackerConfiguration(
            configurationReaderService = commonComponents.configurationReaderService,
            coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        )
    } else {
        null
    }

    private val partitionsStates = if (features.enableP2PStatefulDeliveryTracker) {
        PartitionsStates(
            coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
            stateManager = commonComponents.stateManager,
            config = deliveryTrackerConfig!!,
            clock = commonComponents.clock,
        )
    } else {
        null
    }

    private val ackMessageProcessor: AckMessageProcessor = if (features.enableP2PStatefulDeliveryTracker) {
        AckMessageProcessorImpl(partitionsStates!!)
    } else {
        AckMessageProcessor { _, _ -> TODO("Not yet implemented") }
    }

    private val outboundMessageProcessor = OutboundMessageProcessor(
        commonComponents.sessionManager,
        linkManagerHostingMap,
        groupPolicyProvider,
        membershipGroupReaderProvider,
        commonComponents.messagesPendingSession,
        clock,
        commonComponents.messageConverter,
        ackMessageProcessor,
    )
    private val deliveryTracker = DeliveryTracker(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        messagingConfiguration,
        subscriptionFactory,
        commonComponents.sessionManager,
        clock = clock
    ) { outboundMessageProcessor.processReplayedAuthenticatedMessage(it) }

    private val subscriptionConfig = SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.P2P_OUT_TOPIC)

    private val outboundMessageSubscription = {
        subscriptionFactory.createEventLogSubscription(
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
            partitionsStates = partitionsStates!!,
            config = deliveryTrackerConfig!!,
        )
        ComplexDominoTile(
            OUTBOUND_MESSAGE_PROCESSOR_GROUP,
            coordinatorFactory = lifecycleCoordinatorFactory,
            dependentChildren = listOf(
                statefulDeliveryTracker.dominoTile.coordinatorName,
                publisher.dominoTile.coordinatorName,
                partitionsStates.dominoTile.coordinatorName,
                deliveryTrackerConfig.dominoTile.coordinatorName,
            ),
            managedChildren = listOf(
                statefulDeliveryTracker.dominoTile.toNamedLifecycle(),
                publisher.dominoTile.toNamedLifecycle(),
                partitionsStates.dominoTile.toNamedLifecycle(),
                deliveryTrackerConfig.dominoTile.toNamedLifecycle(),
            ),
        )
    } else {
        SubscriptionDominoTile(
            lifecycleCoordinatorFactory,
            outboundMessageSubscription,
            subscriptionConfig,
            dependentChildren = listOf(
                deliveryTracker.dominoTile.coordinatorName,
                commonComponents.dominoTile.coordinatorName,
            ),
            managedChildren = setOf(deliveryTracker.dominoTile.toNamedLifecycle())
        )
    }
}
