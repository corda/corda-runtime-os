package net.corda.membership.impl.read

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.impl.read.lifecycle.MembershipGroupReadLifecycleHandler
import net.corda.membership.impl.read.reader.MembershipGroupReaderFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

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
@Suppress("LongParameterList")
class MembershipGroupReaderProviderImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
    @Reference(service = GroupParametersReaderService::class)
    private val groupParametersReaderService: GroupParametersReaderService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : MembershipGroupReaderProvider {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val ILLEGAL_ACCESS = "Tried to read group data before starting the component " +
                "or while the component is down."
    }

    // Handler for lifecycle events.
    private val lifecycleHandler = MembershipGroupReadLifecycleHandler.Impl(
        configurationReadService,
        subscriptionFactory,
        memberInfoFactory,
        ::activate,
        ::deactivate,
    )

    // Component lifecycle coordinator
    private val coordinator =
        coordinatorFactory.createCoordinator<MembershipGroupReaderProvider>(lifecycleHandler)

    private var impl: InnerMembershipGroupReaderProvider = InactiveImpl

    private fun activate(reason: String, membershipGroupReadCache: MembershipGroupReadCache) {
        impl.close()
        impl = ActiveImpl(membershipGroupReadCache)
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    private fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        val current = impl
        impl = InactiveImpl
        current.close()
    }


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
    override fun getGroupReader(holdingIdentity: HoldingIdentity) = impl.getGroupReader(holdingIdentity)

    /**
     * Private interface to allow implementation switching internally for thread safety.
     */
    private interface InnerMembershipGroupReaderProvider : AutoCloseable {
        fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader
    }

    private inner class ActiveImpl(
        // Group data cache instance shared across services.
        private val membershipGroupReadCache: MembershipGroupReadCache,
    ) : InnerMembershipGroupReaderProvider {
        // Factory responsible for creating group readers or taking existing instances from the cache.
        private val membershipGroupReaderFactory: MembershipGroupReaderFactory = MembershipGroupReaderFactory.Impl(
            membershipGroupReadCache, groupParametersReaderService, memberInfoFactory, platformInfoProvider
        )

        /**
         * Get the [MembershipGroupReader] instance for the given holding identity.
         */
        override fun getGroupReader(
            holdingIdentity: HoldingIdentity
        ) = membershipGroupReaderFactory.getGroupReader(holdingIdentity)

        override fun close() {
            membershipGroupReadCache.clear()
        }
    }

    private object InactiveImpl : InnerMembershipGroupReaderProvider {
        override fun getGroupReader(
            holdingIdentity: HoldingIdentity
        ): MembershipGroupReader {
            logger.error("Service is in incorrect state for accessing group readers.")
            throw IllegalStateException(ILLEGAL_ACCESS)
        }

        override fun close() = Unit
    }
}
