package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.schema.Schemas

internal class CommonComponents(
    linkManager: LinkManager,
) : LifecycleWithDominoTile {
    internal val inboundAssignmentListener = InboundAssignmentListener(
        linkManager.lifecycleCoordinatorFactory,
        Schemas.P2P.LINK_IN_TOPIC
    )

    internal val messagesPendingSession = PendingSessionMessageQueuesImpl(
        linkManager.publisherFactory,
        linkManager.lifecycleCoordinatorFactory,
        linkManager.configuration
    )

    internal val sessionManager = SessionManagerImpl(
        linkManager.groups,
        linkManager.members,
        linkManager.linkManagerCryptoProcessor,
        messagesPendingSession,
        linkManager.publisherFactory,
        linkManager.configurationReaderService,
        linkManager.lifecycleCoordinatorFactory,
        linkManager.configuration,
        inboundAssignmentListener,
        linkManager.linkManagerHostingMap,
        clock = linkManager.clock,
    )

    private val trustStoresPublisher = TrustStoresPublisher(
        linkManager.subscriptionFactory,
        linkManager.publisherFactory,
        linkManager.lifecycleCoordinatorFactory,
        linkManager.configuration,
    ).also {
        linkManager.groups.registerListener(it)
    }

    private val tlsCertificatesPublisher = TlsCertificatesPublisher(
        linkManager.subscriptionFactory,
        linkManager.publisherFactory,
        linkManager.lifecycleCoordinatorFactory,
        linkManager.configuration,
    ).also {
        linkManager.linkManagerHostingMap.registerListener(it)
    }

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        linkManager.lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            linkManager.groups.dominoTile,
            linkManager.members.dominoTile,
            linkManager.linkManagerHostingMap.dominoTile,
            linkManager.linkManagerCryptoProcessor.dominoTile,
            messagesPendingSession.dominoTile,
            sessionManager.dominoTile,
            trustStoresPublisher.dominoTile,
            tlsCertificatesPublisher.dominoTile,
        ),
        managedChildren = listOf(
            linkManager.groups.dominoTile,
            linkManager.members.dominoTile,
            linkManager.linkManagerHostingMap.dominoTile,
            linkManager.linkManagerCryptoProcessor.dominoTile,
            messagesPendingSession.dominoTile,
            sessionManager.dominoTile,
            trustStoresPublisher.dominoTile,
            tlsCertificatesPublisher.dominoTile,
        )
    )
}
