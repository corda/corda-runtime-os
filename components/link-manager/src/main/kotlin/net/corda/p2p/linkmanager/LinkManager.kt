package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.grouppolicy.ForwardingGroupPolicyProvider
import net.corda.p2p.linkmanager.grouppolicy.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMapImpl
import net.corda.p2p.linkmanager.inbound.InboundLinkManager
import net.corda.p2p.linkmanager.membership.ForwardingMembershipGroupReader
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.outbound.OutboundLinkManager
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.UUID

@Suppress("LongParameterList")
class LinkManager(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    messagingConfiguration: SmartConfig,
    groupPolicyProvider: GroupPolicyProvider,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    cryptoOpsClient: CryptoOpsClient,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    membershipQueryClient: MembershipQueryClient,
    groupParametersReaderService: GroupParametersReaderService,
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

    private val forwardingGroupPolicyProvider: LinkManagerGroupPolicyProvider = ForwardingGroupPolicyProvider(
        lifecycleCoordinatorFactory, groupPolicyProvider,
            virtualNodeInfoReadService, cpiInfoReadService, membershipQueryClient)

    private val members: LinkManagerMembershipGroupReader = ForwardingMembershipGroupReader(
        membershipGroupReaderProvider, lifecycleCoordinatorFactory, groupParametersReaderService)


    private val commonComponents = CommonComponents(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        linkManagerHostingMap = linkManagerHostingMap,
        groups = forwardingGroupPolicyProvider,
        members = members,
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
        groups = forwardingGroupPolicyProvider,
        members = members,
        configurationReaderService = configurationReaderService,
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        messagingConfiguration = messagingConfiguration,
        clock = clock,
    )
    private val inboundLinkManager = InboundLinkManager(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        commonComponents = commonComponents,
        groups = forwardingGroupPolicyProvider,
        members = members,
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
