package net.corda.p2p.linkmanager

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.registry.LifecycleRegistry
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
    lifecycleRegistry: LifecycleRegistry,
    configurationReaderService: ConfigurationReadService,
    messagingConfiguration: SmartConfig,
    groups: LinkManagerGroupPolicyProvider = StubGroupPolicyProvider(
        lifecycleCoordinatorFactory, lifecycleRegistry, subscriptionFactory, messagingConfiguration
    ),
    members: LinkManagerMembershipGroupReader = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, lifecycleRegistry, subscriptionFactory, messagingConfiguration
    ),
    linkManagerHostingMap: LinkManagerHostingMap =
        StubLinkManagerHostingMap(
            lifecycleCoordinatorFactory,
            lifecycleRegistry,
            subscriptionFactory,
            messagingConfiguration,
        ),
    linkManagerCryptoProcessor: CryptoProcessor =
        StubCryptoProcessor(lifecycleCoordinatorFactory, lifecycleRegistry, subscriptionFactory, messagingConfiguration),
    clock: Clock = UTCClock()
) : LifecycleWithDominoTile {

    companion object {
        internal fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    private val commonComponents = CommonComponents(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        registry = lifecycleRegistry,
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
        registry = lifecycleRegistry,
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
        lifecycleRegistry,
        dependentChildren = setOf(
            commonComponents.dominoTile.coordinatorName,
            outboundLinkManager.dominoTile.coordinatorName,
            inboundLinkManager.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(
            commonComponents.dominoTile,
            outboundLinkManager.dominoTile,
            inboundLinkManager.dominoTile,
        )
    )
}
