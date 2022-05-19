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
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Suppress("LongParameterList")
class LinkManager(
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = LifecycleRegistry::class)
    lifecycleRegistry: LifecycleRegistry,
    @Reference(service = ConfigurationReadService::class)
    configurationReaderService: ConfigurationReadService,
    configuration: SmartConfig,
    groups: LinkManagerGroupPolicyProvider = StubGroupPolicyProvider(
        lifecycleCoordinatorFactory, lifecycleRegistry, subscriptionFactory, configuration
    ),
    members: LinkManagerMembershipGroupReader = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, lifecycleRegistry, subscriptionFactory, configuration
    ),
    linkManagerHostingMap: LinkManagerHostingMap =
        StubLinkManagerHostingMap(
            lifecycleCoordinatorFactory,
            lifecycleRegistry,
            subscriptionFactory,
            configuration,
        ),
    linkManagerCryptoProcessor: CryptoProcessor =
        StubCryptoProcessor(lifecycleCoordinatorFactory, lifecycleRegistry, subscriptionFactory, configuration),
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
        configuration = configuration,
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
        configuration = configuration,
        clock = clock,
    )
    private val inboundLinkManager = InboundLinkManager(
        lifecycleCoordinatorFactory = lifecycleCoordinatorFactory,
        commonComponents = commonComponents,
        groups = groups,
        members = members,
        subscriptionFactory = subscriptionFactory,
        configuration = configuration,
        clock = clock,
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        lifecycleRegistry,
        dependentChildren = setOf(
            commonComponents.dominoTile,
            outboundLinkManager.dominoTile,
            inboundLinkManager.dominoTile,
        ),
        managedChildren = setOf(
            commonComponents.dominoTile,
            outboundLinkManager.dominoTile,
            inboundLinkManager.dominoTile,
        )
    )
}
