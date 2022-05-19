package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock

@Suppress("LongParameterList")
internal class CommonComponents(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    registry: LifecycleRegistry,
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
    internal val inboundAssignmentListener = InboundAssignmentListener(
        lifecycleCoordinatorFactory,
        Schemas.P2P.LINK_IN_TOPIC
    )

    internal val messagesPendingSession = PendingSessionMessageQueuesImpl(
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration
    )

    internal val sessionManager = SessionManagerImpl(
        groups,
        members,
        linkManagerCryptoProcessor,
        messagesPendingSession,
        publisherFactory,
        configurationReaderService,
        lifecycleCoordinatorFactory,
        registry,
        configuration,
        inboundAssignmentListener,
        linkManagerHostingMap,
        clock = clock,
    )

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        registry,
        configuration,
    ).also {
        groups.registerListener(it)
    }

    private val tlsCertificatesPublisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        registry,
        configuration,
    ).also {
        linkManagerHostingMap.registerListener(it)
    }

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        registry,
        dependentChildren = listOf(
            groups.dominoTile,
            members.dominoTile,
            linkManagerHostingMap.dominoTile,
            linkManagerCryptoProcessor.dominoTile,
            messagesPendingSession.dominoTile,
            sessionManager.dominoTile,
            trustStoresPublisher.dominoTile,
            tlsCertificatesPublisher.dominoTile,
        ),
        managedChildren = listOf(
            groups.dominoTile,
            members.dominoTile,
            linkManagerHostingMap.dominoTile,
            linkManagerCryptoProcessor.dominoTile,
            messagesPendingSession.dominoTile,
            sessionManager.dominoTile,
            trustStoresPublisher.dominoTile,
            tlsCertificatesPublisher.dominoTile,
        )
    )
}
