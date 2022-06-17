package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
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
    groups: LinkManagerGroupPolicyProvider = StubGroupPolicyProvider(
        lifecycleCoordinatorFactory, subscriptionFactory, messagingConfiguration
    ),
    members: LinkManagerMembershipGroupReader = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, subscriptionFactory, messagingConfiguration
    ),
    linkManagerHostingMap: LinkManagerHostingMap =
        StubLinkManagerHostingMap(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            messagingConfiguration,
        ),
    linkManagerCryptoProcessor: CryptoProcessor =
        StubCryptoProcessor(lifecycleCoordinatorFactory, subscriptionFactory, messagingConfiguration),
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
        groups = groups,
        members = members,
        configurationReaderService = configurationReaderService,
        linkManagerCryptoProcessor = linkManagerCryptoProcessor,
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        messagingConfiguration = messagingConfiguration,
        clock = clock,
    )
    private val outboundLinkManager = OutboundLinkManager(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        commonComponents = commonComponents,
        linkManagerHostingMap = linkManagerHostingMap,
        groups = groups,
        members = members,
        configurationReaderService = configurationReaderService,
        linkManagerCryptoProcessor = linkManagerCryptoProcessor,
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        messagingConfiguration = messagingConfiguration,
        clock = clock,
    )
    private val inboundLinkManager = InboundLinkManager(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        commonComponents = commonComponents,
        groups = groups,
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
            commonComponents.dominoTile.toLifecycleWithCoordinatorName(),
            outboundLinkManager.dominoTile.toLifecycleWithCoordinatorName(),
            inboundLinkManager.dominoTile.toLifecycleWithCoordinatorName(),
        )
    )
}
