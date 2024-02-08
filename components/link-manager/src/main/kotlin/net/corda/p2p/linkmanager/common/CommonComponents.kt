package net.corda.p2p.linkmanager.common

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
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
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.forwarding.gateway.TlsCertificatesPublisher
import net.corda.p2p.linkmanager.forwarding.gateway.TrustStoresPublisher
import net.corda.p2p.linkmanager.forwarding.gateway.mtls.ClientCertificatePublisher
import net.corda.p2p.linkmanager.inbound.InboundAssignmentListener
import net.corda.p2p.linkmanager.sessions.PendingSessionMessageQueuesImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.StateConvertor
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl
import net.corda.schema.Schemas
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.flags.Features
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService

@Suppress("LongParameterList")
internal class CommonComponents(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    linkManagerHostingMap: LinkManagerHostingMap,
    groupPolicyProvider: GroupPolicyProvider,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    configurationReaderService: ConfigurationReadService,
    cryptoOpsClient: CryptoOpsClient,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    membershipQueryClient: MembershipQueryClient,
    groupParametersReaderService: GroupParametersReaderService,
    clock: Clock,
    internal val stateManager: StateManager,
    schemaRegistry: AvroSchemaRegistry,
    sessionEncryptionOpsClient: SessionEncryptionOpsClient,
    features: Features = Features(),
) : LifecycleWithDominoTile {
    private companion object {
        const val LISTENER_NAME = "link.manager.group.policy.listener"
    }
    internal val inboundAssignmentListener = InboundAssignmentListener(
        lifecycleCoordinatorFactory,
        Schemas.P2P.LINK_IN_TOPIC
    )

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

    internal val sessionManager = if (features.useStatefulSessionManager) {
        StatefulSessionManagerImpl(
            publisherFactory,
            subscriptionFactory,
            messagingConfiguration,
            lifecycleCoordinatorFactory,
            stateManager,
            SessionManagerImpl(
                groupPolicyProvider,
                membershipGroupReaderProvider,
                cryptoOpsClient,
                messagesPendingSession,
                publisherFactory,
                configurationReaderService,
                lifecycleCoordinatorFactory,
                messagingConfiguration,
                inboundAssignmentListener,
                linkManagerHostingMap,
                clock = clock,
                trackSessionHealthAndReplaySessionMessages = false
            ),
            StateConvertor(
                schemaRegistry,
                sessionEncryptionOpsClient,
            ),
            clock,
            membershipGroupReaderProvider,
            schemaRegistry,
        )
    } else {
        SessionManagerImpl(
            groupPolicyProvider,
            membershipGroupReaderProvider,
            cryptoOpsClient,
            messagesPendingSession,
            publisherFactory,
            configurationReaderService,
            lifecycleCoordinatorFactory,
            messagingConfiguration,
            inboundAssignmentListener,
            linkManagerHostingMap,
            clock = clock,
        )
    }

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
    ).also {
        groupPolicyProvider.registerListener(LISTENER_NAME) { holdingIdentity, groupPolicy ->
            it.groupAdded(holdingIdentity, groupPolicy)
        }
    }

    private val tlsCertificatesPublisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
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
    )

    private val externalDependencies = listOf(
        NamedLifecycle.of(groupPolicyProvider),
        NamedLifecycle.of(membershipGroupReaderProvider),
        NamedLifecycle.of(cryptoOpsClient),
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
            sessionManager.dominoTile.coordinatorName,
            trustStoresPublisher.dominoTile.coordinatorName,
            tlsCertificatesPublisher.dominoTile.coordinatorName,
            mtlsClientCertificatePublisher.dominoTile.coordinatorName,
        ) + externalDependencies.map {
            it.name
        } + stateManager.name,
        managedChildren = listOf(
            linkManagerHostingMap.dominoTile.toNamedLifecycle(),
            messagesPendingSession.dominoTile.toNamedLifecycle(),
            sessionManager.dominoTile.toNamedLifecycle(),
            trustStoresPublisher.dominoTile.toNamedLifecycle(),
            tlsCertificatesPublisher.dominoTile.toNamedLifecycle(),
            mtlsClientCertificatePublisher.dominoTile.toNamedLifecycle(),
        ) + externalManagedDependencies
    )
}
