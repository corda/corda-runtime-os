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
import net.corda.membership.read.MembershipGroupReadService
import net.corda.membership.read.MembershipGroupReader
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Membership component implementing [MembershipGroupReadService].
 * This component is to be used for internal lookups of membership group data. This includes member lists, group
 * parameters, CPI whitelists and group policies.
 */
@Component(service = [MembershipGroupReadService::class])
class MembershipGroupReadServiceImpl @Activate constructor(
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
) : MembershipGroupReadService, Lifecycle {

    // Group data cache instance shared across services.
    private val membershipGroupReadCache = MembershipGroupReadCache.Impl()

    // Factory responsible for creating group readers or taking existing instances from the cache.
    private val membershipGroupReaderFactory = MembershipGroupReaderFactory.Impl(
        virtualNodeInfoReader,
        cpiInfoReader,
        membershipGroupReadCache
    )

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
        coordinatorFactory.createCoordinator<MembershipGroupReadService>(lifecycleHandler)

    // Component is running when it's subscriptions are active.
    override val isRunning
        get() = membershipGroupReadSubscriptions.isRunning

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
        groupId: String,
        memberX500Name: MemberX500Name
    ) = membershipGroupReaderFactory.getGroupReader(groupId, memberX500Name)
}