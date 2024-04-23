package net.corda.p2p.linkmanager

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
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
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMapImpl
import net.corda.p2p.linkmanager.inbound.InboundLinkManager
import net.corda.p2p.linkmanager.outbound.OutboundLinkManager
import net.corda.p2p.linkmanager.sessions.SessionManagerCommonComponents
import net.corda.schema.registry.AvroSchemaRegistry
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
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    messagingConfiguration: SmartConfig,
    groupPolicyProvider: GroupPolicyProvider,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    cryptoOpsClient: CryptoOpsClient,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    membershipQueryClient: MembershipQueryClient,
    groupParametersReaderService: GroupParametersReaderService,
    stateManager: StateManager,
    sessionEncryptionOpsClient: SessionEncryptionOpsClient,
    schemaRegistry: AvroSchemaRegistry,
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
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        messagingConfiguration = messagingConfiguration,
        cpiInfoReadService = cpiInfoReadService,
        membershipQueryClient = membershipQueryClient,
        virtualNodeInfoReadService = virtualNodeInfoReadService,
        groupParametersReaderService = groupParametersReaderService,
        clock = clock,
        stateManager = stateManager,
        schemaRegistry = schemaRegistry,
        sessionEncryptionOpsClient = sessionEncryptionOpsClient,
    )
    private val sessionManagerCommonComponents = SessionManagerCommonComponents(
        cryptoOpsClient,
        cordaAvroSerializationFactory,
        commonComponents,
    )
    private val outboundLinkManager = OutboundLinkManager(
        commonComponents = commonComponents,
        messagingConfiguration = messagingConfiguration,
        sessionComponents = sessionManagerCommonComponents,
    )
    private val inboundLinkManager = InboundLinkManager(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        commonComponents = sessionManagerCommonComponents,
        groupPolicyProvider = groupPolicyProvider,
        membershipGroupReaderProvider = membershipGroupReaderProvider,
        subscriptionFactory = subscriptionFactory,
        messagingConfiguration = messagingConfiguration,
        publisherFactory = publisherFactory,
        clock = clock,
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(
            commonComponents.dominoTile.coordinatorName,
            sessionManagerCommonComponents.dominoTile.coordinatorName,
            outboundLinkManager.dominoTile.coordinatorName,
            inboundLinkManager.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(
            commonComponents.dominoTile.toNamedLifecycle(),
            sessionManagerCommonComponents.dominoTile.toNamedLifecycle(),
            outboundLinkManager.dominoTile.toNamedLifecycle(),
            inboundLinkManager.dominoTile.toNamedLifecycle(),
        )
    )
}
