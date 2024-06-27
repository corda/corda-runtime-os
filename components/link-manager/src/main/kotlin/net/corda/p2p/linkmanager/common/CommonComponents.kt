package net.corda.p2p.linkmanager.common

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.forwarding.gateway.TlsCertificatesPublisher
import net.corda.p2p.linkmanager.forwarding.gateway.TrustStoresPublisher
import net.corda.p2p.linkmanager.forwarding.gateway.mtls.ClientCertificatePublisher
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.sessions.PendingSessionMessageQueuesImpl
import net.corda.p2p.linkmanager.state.StateConvertor
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService

@Suppress("LongParameterList")
internal class CommonComponents(
    internal val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    internal val linkManagerHostingMap: LinkManagerHostingMap,
    internal val groupPolicyProvider: GroupPolicyProvider,
    internal val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    internal val subscriptionFactory: SubscriptionFactory,
    internal val publisherFactory: PublisherFactory,
    internal val messagingConfiguration: SmartConfig,
    internal val configurationReaderService: ConfigurationReadService,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    membershipQueryClient: MembershipQueryClient,
    groupParametersReaderService: GroupParametersReaderService,
    internal val clock: Clock,
    internal val schemaRegistry: AvroSchemaRegistry,
    internal val stateManager: StateManager,
    sessionEncryptionOpsClient: SessionEncryptionOpsClient,
) : LifecycleWithDominoTile {
    private companion object {
        const val LISTENER_NAME = "link.manager.group.policy.listener"
    }

    internal val messageConverter = MessageConverter(
        groupPolicyProvider,
        membershipGroupReaderProvider,
        clock,
    )

    internal val messagesPendingSession = PendingSessionMessageQueuesImpl(
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        messageConverter,
    )

    internal val stateConvertor = StateConvertor(
        schemaRegistry,
        sessionEncryptionOpsClient,
    )

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configurationReaderService,
        messagingConfiguration,
    ).also {
        groupPolicyProvider.registerListener(LISTENER_NAME) { holdingIdentity, groupPolicy ->
            it.groupAdded(holdingIdentity, groupPolicy)
        }
    }

    private val tlsCertificatesPublisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
        configurationReaderService,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
    ).also {
        linkManagerHostingMap.registerListener(it)
    }

    private val mtlsClientCertificatePublisher = ClientCertificatePublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        groupPolicyProvider,
        configurationReaderService,
    )

    private val externalDependencies = listOf(
        NamedLifecycle.of(groupPolicyProvider),
        NamedLifecycle.of(membershipGroupReaderProvider),
    )

    private val externalManagedDependencies = listOf(
        NamedLifecycle.of(virtualNodeInfoReadService),
        NamedLifecycle.of(cpiInfoReadService),
        NamedLifecycle.of(membershipQueryClient),
        NamedLifecycle.of(groupParametersReaderService),
        NamedLifecycle.of(sessionEncryptionOpsClient),
    ) + externalDependencies

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            linkManagerHostingMap.dominoTile.coordinatorName,
            messagesPendingSession.dominoTile.coordinatorName,
            trustStoresPublisher.dominoTile.coordinatorName,
            tlsCertificatesPublisher.dominoTile.coordinatorName,
            mtlsClientCertificatePublisher.dominoTile.coordinatorName,
        ) + externalDependencies.map {
            it.name
        } + stateManager.name,
        managedChildren = listOf(
            linkManagerHostingMap.dominoTile.toNamedLifecycle(),
            messagesPendingSession.dominoTile.toNamedLifecycle(),
            trustStoresPublisher.dominoTile.toNamedLifecycle(),
            tlsCertificatesPublisher.dominoTile.toNamedLifecycle(),
            mtlsClientCertificatePublisher.dominoTile.toNamedLifecycle(),
        ) + externalManagedDependencies,
    )
}
