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
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import java.util.concurrent.Executors

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
) : LifecycleWithDominoTile {
    companion object {
        private const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"
        private const val OUTBOUND_MESSAGE_PROCESSOR_ID = "outbound_message_processor"

    }
    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(OUTBOUND_MESSAGE_PROCESSOR_ID),
        messagingConfiguration
    )
    private val scheduledExecutor =
        Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, OUTBOUND_MESSAGE_PROCESSOR_ID) }
    private val outboundMessageProcessor = OutboundMessageProcessor(
        commonComponents.sessionManager,
        linkManagerHostingMap,
        groupPolicyProvider,
        membershipGroupReaderProvider,
        commonComponents.inboundAssignmentListener,
        commonComponents.messagesPendingSession,
        clock,
        commonComponents.messageConverter,
        publisher,
        scheduledExecutor,
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

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        outboundMessageSubscription,
        subscriptionConfig,
        configurationReaderService,
        dependentChildren = listOf(
            deliveryTracker.dominoTile.coordinatorName,
            commonComponents.dominoTile.coordinatorName,
            commonComponents.inboundAssignmentListener.dominoTile.coordinatorName,
            publisher.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(
            deliveryTracker.dominoTile.toNamedLifecycle(),
            publisher.dominoTile.toNamedLifecycle(),
        )
    )

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        onClose = { scheduledExecutor.shutdown() },
        dependentChildren = setOf(
            subscriptionTile.coordinatorName,
        ),
        managedChildren = setOf(
            subscriptionTile.toNamedLifecycle(),
        )
    )
}
