package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMapImpl
import net.corda.p2p.linkmanager.inbound.InboundLinkManager
import net.corda.p2p.linkmanager.outbound.OutboundLinkManager
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import java.util.UUID

@Suppress("LongParameterList")
class LinkManager(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    messagingConfiguration: SmartConfig,
    groupPolicyProvider: GroupPolicyProvider,
    cryptoOpsClient: CryptoOpsClient,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    linkManagerHostingMap: LinkManagerHostingMap =
        LinkManagerHostingMapImpl(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            messagingConfiguration,
        ),
    clock: Clock = UTCClock()
) : LifecycleWithDominoTile {

    companion object {
        internal fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    private val commonComponents = CommonComponents(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        linkManagerHostingMap = linkManagerHostingMap,
        groupPolicyProvider = groupPolicyProvider,
        membershipGroupReaderProvider = membershipGroupReaderProvider,
        configurationReaderService = configurationReaderService,
        cryptoOpsClient = cryptoOpsClient,
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        messagingConfiguration = messagingConfiguration,
        clock = clock
    )
    private val outboundLinkManager = OutboundLinkManager(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        commonComponents = commonComponents,
        linkManagerHostingMap = linkManagerHostingMap,
        groupPolicyProvider = groupPolicyProvider,
        membershipGroupReaderProvider = membershipGroupReaderProvider,
        configurationReaderService = configurationReaderService,
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        messagingConfiguration = messagingConfiguration,
        clock = clock,
    )
    private val inboundLinkManager = InboundLinkManager(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        commonComponents = commonComponents,
        groupPolicyProvider = groupPolicyProvider,
        membershipGroupReaderProvider = membershipGroupReaderProvider,
        subscriptionFactory = subscriptionFactory,
        messagingConfiguration = messagingConfiguration,
        clock = clock,
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(
            commonComponents.dominoTile.coordinatorName,
            outboundLinkManager.dominoTile.coordinatorName,
            inboundLinkManager.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(
            commonComponents.dominoTile.toNamedLifecycle(),
            outboundLinkManager.dominoTile.toNamedLifecycle(),
            inboundLinkManager.dominoTile.toNamedLifecycle(),
        )
    )
}
