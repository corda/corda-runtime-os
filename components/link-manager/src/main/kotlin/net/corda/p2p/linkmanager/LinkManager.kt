package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.delivery.DeliveryTracker
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
import net.corda.schema.Schemas.P2P.Companion.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import org.osgi.service.component.annotations.Reference
import java.time.Clock
import java.util.UUID

@Suppress("LongParameterList")
class LinkManager(
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReaderService: ConfigurationReadService,
    configuration: SmartConfig,
    groups: LinkManagerGroupPolicyProvider = StubGroupPolicyProvider(
        lifecycleCoordinatorFactory, subscriptionFactory, configuration
    ),
    members: LinkManagerMembershipGroupReader = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, subscriptionFactory, configuration
    ),
    linkManagerHostingMap: LinkManagerHostingMap =
        StubLinkManagerHostingMap(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            configuration,
        ),
    linkManagerCryptoProcessor: CryptoProcessor =
        StubCryptoProcessor(lifecycleCoordinatorFactory, subscriptionFactory, configuration),
    clock: Clock = Clock.systemUTC()
) : LifecycleWithDominoTile {

    companion object {
        const val LINK_MANAGER_PUBLISHER_CLIENT_ID = "linkmanager"
        const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
        const val OUTBOUND_MESSAGE_PROCESSOR_GROUP = "outbound_message_processor_group"

        fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    private var inboundAssignmentListener = InboundAssignmentListener(lifecycleCoordinatorFactory, LINK_IN_TOPIC)

    private val messagesPendingSession = PendingSessionMessageQueuesImpl(
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration
    )

    private val sessionManager = SessionManagerImpl(
        groups,
        members,
        linkManagerCryptoProcessor,
        messagesPendingSession,
        publisherFactory,
        configurationReaderService,
        lifecycleCoordinatorFactory,
        configuration,
        inboundAssignmentListener,
        linkManagerHostingMap,
        clock = clock
    )

    private val outboundMessageProcessor = OutboundMessageProcessor(
        sessionManager,
        linkManagerHostingMap,
        groups,
        members,
        inboundAssignmentListener,
        messagesPendingSession,
        clock
    )

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
    ).also {
        groups.registerListener(it)
    }

    private val tlsCertificatesPublisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
    ).also {
        linkManagerHostingMap.registerListener(it)
    }

    private val deliveryTracker = DeliveryTracker(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        configuration,
        subscriptionFactory,
        groups,
        members,
        linkManagerCryptoProcessor,
        sessionManager,
        clock = clock
    ) { outboundMessageProcessor.processReplayedAuthenticatedMessage(it) }

    private val inboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, LINK_IN_TOPIC),
        InboundMessageProcessor(
            sessionManager,
            groups,
            members,
            inboundAssignmentListener,
            clock
        ),
        configuration,
        partitionAssignmentListener = inboundAssignmentListener
    )

    private val outboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(OUTBOUND_MESSAGE_PROCESSOR_GROUP, P2P_OUT_TOPIC),
        outboundMessageProcessor,
        configuration,
        partitionAssignmentListener = null
    )

    private val commonChildren = setOf(
        groups.dominoTile,
        members.dominoTile,
        linkManagerCryptoProcessor.dominoTile,
        linkManagerHostingMap.dominoTile
    )
    private val inboundSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        inboundMessageSubscription,
        dependentChildren = commonChildren,
        managedChildren = setOf(inboundAssignmentListener.dominoTile)
    )
    private val outboundSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        outboundMessageSubscription,
        dependentChildren = commonChildren + setOf(
            messagesPendingSession.dominoTile,
            inboundAssignmentListener.dominoTile
        ),
        managedChildren = setOf(messagesPendingSession.dominoTile)
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(
            inboundSubscriptionTile,
            outboundSubscriptionTile,
            deliveryTracker.dominoTile,
        ),
        managedChildren = setOf(
            inboundSubscriptionTile,
            outboundSubscriptionTile,
            deliveryTracker.dominoTile,
            sessionManager.dominoTile,
            trustStoresPublisher.dominoTile,
            tlsCertificatesPublisher.dominoTile,
        ) + commonChildren
    )
}
