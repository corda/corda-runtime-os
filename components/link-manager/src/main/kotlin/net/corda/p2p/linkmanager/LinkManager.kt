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
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Suppress("LongParameterList")
class LinkManager(
    @Reference(service = SubscriptionFactory::class)
    internal val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    internal val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    internal val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    internal val configurationReaderService: ConfigurationReadService,
    internal val configuration: SmartConfig,
    internal val groups: LinkManagerGroupPolicyProvider = StubGroupPolicyProvider(
        lifecycleCoordinatorFactory, subscriptionFactory, configuration
    ),
    internal val members: LinkManagerMembershipGroupReader = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, subscriptionFactory, configuration
    ),
    internal val linkManagerHostingMap: LinkManagerHostingMap =
        StubLinkManagerHostingMap(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            configuration,
        ),
    internal val linkManagerCryptoProcessor: CryptoProcessor =
        StubCryptoProcessor(lifecycleCoordinatorFactory, subscriptionFactory, configuration),
    internal val clock: Clock = UTCClock()
) : LifecycleWithDominoTile {

    companion object {
        internal fun generateKey(): String {
            return UUID.randomUUID().toString()
        }
    }

    internal val commonTile = CommonTile(this)
    private val outboundTile = OutboundTile(this)
    private val inboundTile = InboundTile(this)

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(
            commonTile.dominoTile,
            outboundTile.dominoTile,
            inboundTile.dominoTile,
        ),
        managedChildren = setOf(
            commonTile.dominoTile,
            outboundTile.dominoTile,
            inboundTile.dominoTile,
        )
    )
}
