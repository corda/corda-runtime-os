package net.corda.p2p.linkmanager.outbound

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock

@Suppress("LongParameterList")
internal class OutboundLinkManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    commonComponents: CommonComponents,
    linkManagerHostingMap: LinkManagerHostingMap,
    groupPolicyProvider: GroupPolicyProvider,
    members: LinkManagerMembershipGroupReader,
    configurationReaderService: ConfigurationReadService,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    clock: Clock,
) : LifecycleWithDominoTile {
    companion object {
        private const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"
    }
    private val outboundMessageProcessor = OutboundMessageProcessor(
        commonComponents.sessionManager,
        linkManagerHostingMap,
        groupPolicyProvider,
        members,
        commonComponents.inboundAssignmentListener,
        commonComponents.messagesPendingSession,
        clock
    )
    private val deliveryTracker = DeliveryTracker(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        messagingConfiguration,
        subscriptionFactory,
        members,
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

    override val dominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        outboundMessageSubscription,
        subscriptionConfig,
        dependentChildren = listOf(
            deliveryTracker.dominoTile.coordinatorName,
            commonComponents.dominoTile.coordinatorName,
            commonComponents.inboundAssignmentListener.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(deliveryTracker.dominoTile.toNamedLifecycle())
    )
}
