package net.corda.p2p.linkmanager.common

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.forwarding.gateway.TlsCertificatesPublisher
import net.corda.p2p.linkmanager.forwarding.gateway.TrustStoresPublisher
import net.corda.p2p.linkmanager.inbound.InboundAssignmentListener
import net.corda.p2p.linkmanager.sessions.PendingSessionMessageQueuesImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock

@Suppress("LongParameterList")
internal class CommonComponents(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    linkManagerHostingMap: LinkManagerHostingMap,
    groupPolicyProvider: GroupPolicyProvider,
    members: LinkManagerMembershipGroupReader,
    configurationReaderService: ConfigurationReadService,
    cryptoOpsClient: CryptoOpsClient,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    clock: Clock,
) : LifecycleWithDominoTile {
    private companion object {
        const val LISTENER_NAME = "link.manager.group.policy.listener"
    }
    internal val inboundAssignmentListener = InboundAssignmentListener(
        lifecycleCoordinatorFactory,
        Schemas.P2P.LINK_IN_TOPIC
    )

    internal val messagesPendingSession = PendingSessionMessageQueuesImpl(
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration
    )

    internal val sessionManager = SessionManagerImpl(
        groupPolicyProvider,
        members,
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

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            members.dominoTile.coordinatorName,
            linkManagerHostingMap.dominoTile.coordinatorName,
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            messagesPendingSession.dominoTile.coordinatorName,
            sessionManager.dominoTile.coordinatorName,
            trustStoresPublisher.dominoTile.coordinatorName,
            tlsCertificatesPublisher.dominoTile.coordinatorName,
        ),
        managedChildren = listOf(
            NamedLifecycle(groupPolicyProvider, LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()),
            members.dominoTile.toNamedLifecycle(),
            linkManagerHostingMap.dominoTile.toNamedLifecycle(),
            NamedLifecycle(cryptoOpsClient, LifecycleCoordinatorName.forComponent<CryptoOpsClient>()),
            messagesPendingSession.dominoTile.toNamedLifecycle(),
            sessionManager.dominoTile.toNamedLifecycle(),
            trustStoresPublisher.dominoTile.toNamedLifecycle(),
            tlsCertificatesPublisher.dominoTile.toNamedLifecycle(),
        )
    )
}
