package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock

@Suppress("LongParameterList")
internal class OutboundLinkManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    commonComponents: CommonComponents,
    linkManagerHostingMap: LinkManagerHostingMap,
    groups: LinkManagerGroupPolicyProvider,
    members: LinkManagerMembershipGroupReader,
    configurationReaderService: ConfigurationReadService,
    linkManagerCryptoProcessor: CryptoProcessor,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    configuration: SmartConfig,
    clock: Clock,
) : LifecycleWithDominoTile {
    companion object {
        private const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"
    }
    private val outboundMessageProcessor = OutboundMessageProcessor(
        commonComponents.sessionManager,
        linkManagerHostingMap,
        groups,
        members,
        commonComponents.inboundAssignmentListener,
        commonComponents.messagesPendingSession,
        clock
    )
    private val deliveryTracker = DeliveryTracker(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        configuration,
        subscriptionFactory,
        groups,
        members,
        linkManagerCryptoProcessor,
        commonComponents.sessionManager,
        clock = clock
    ) { outboundMessageProcessor.processReplayedAuthenticatedMessage(it) }

    private val outboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.P2P_OUT_TOPIC),
        outboundMessageProcessor,
        configuration,
        partitionAssignmentListener = null
    )

    override val dominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        outboundMessageSubscription,
        dependentChildren = listOf(
            deliveryTracker.dominoTile,
            commonComponents.dominoTile,
            commonComponents.inboundAssignmentListener.dominoTile,
        ),
        managedChildren = setOf(deliveryTracker.dominoTile)
    )
}
