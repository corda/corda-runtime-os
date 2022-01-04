package net.corda.membership.impl.read.component

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReaderComponent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.impl.read.lifecycle.MembershipGroupReadLifecycleHandler
import net.corda.membership.impl.read.reader.MembershipGroupReaderFactory
import net.corda.membership.impl.read.subscription.MembershipGroupReadSubscriptions
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.MembershipGroupReader
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Membership component implementing [MembershipGroupReaderProvider].
 *
 * This component is to be used for internal lookups of membership group data. This includes member lists, group
 * parameters, CPI whitelists and group policies.
 *
 * Subscriptions for member data are only created after receiving the configuration from the [ConfigurationReadService].
 * Starting the component will start the lifecycle coordinator. At this point the component is running but group data
 * will not be available until subscriptions have been created meaning lookups done before configuration has been
 * received will return no results.
 */
@Component(service = [MembershipGroupReaderProvider::class])
class MembershipGroupReaderProviderImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReaderComponent::class)
    val virtualNodeInfoReader: VirtualNodeInfoReaderComponent,
    @Reference(service = CpiInfoReaderComponent::class)
    val cpiInfoReader: CpiInfoReaderComponent,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory
) : MembershipGroupReaderProvider, Lifecycle {

    companion object {
        const val ACCESS_TOO_EARLY = "Tried to read group data before starting the component."
    }

    // Group data cache instance shared across services.
    private val membershipGroupReadCache = MembershipGroupReadCache.Impl()

    // Factory responsible for creating group readers or taking existing instances from the cache.
    private val membershipGroupReaderFactory = MembershipGroupReaderFactory.Impl(membershipGroupReadCache)

    // Membership group topic subscriptions
    private val membershipGroupReadSubscriptions = MembershipGroupReadSubscriptions.Impl(
        subscriptionFactory,
        membershipGroupReadCache
    )

    // Handler for lifecycle events.
    private val lifecycleHandler = MembershipGroupReadLifecycleHandler.Impl(
        this,
        membershipGroupReadSubscriptions,
        membershipGroupReadCache
    )

    // Component lifecycle coordinator
    private val coordinator =
        coordinatorFactory.createCoordinator<MembershipGroupReaderProvider>(lifecycleHandler)

    // Component is running when it's coordinator has started.
    override val isRunning
        get() = coordinator.isRunning

    /**
     * Start the coordinator, which will then receive the lifecycle event [StartEvent].
     */
    override fun start() = coordinator.start()

    /**
     * Stop the coordinator, which will then receive the lifecycle event [StopEvent].
     */
    override fun stop() = coordinator.stop()

    /**
     * Get the [MembershipGroupReader] instance for the given holding identity.
     */
    override fun getGroupReader(
        holdingIdentity: HoldingIdentity
    ) = if (isRunning) {
        membershipGroupReaderFactory.getGroupReader(holdingIdentity)
    } else {
        throw CordaRuntimeException(ACCESS_TOO_EARLY)
    }
}