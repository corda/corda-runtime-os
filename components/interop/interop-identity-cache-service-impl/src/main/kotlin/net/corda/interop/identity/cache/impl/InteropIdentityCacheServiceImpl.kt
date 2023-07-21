package net.corda.interop.identity.cache.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.cache.InteropIdentityCacheService
import net.corda.interop.identity.cache.InteropIdentityCacheView
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Component(service = [InteropIdentityCacheService::class])
class InteropIdentityCacheServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : InteropIdentityCacheService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleEventHandler = InteropIdentityCacheServiceEventHandler(
        configurationReadService, subscriptionFactory, this
    )

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropIdentityCacheService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleEventHandler)

    /**
     * Map of holding identity short hashes to [InteropIdentityCacheViewImpl] objects
     */
    private val cacheData = HashMap<String, InteropIdentityCacheViewImpl>()

    private fun getOrCreateView(shortHash: String): InteropIdentityCacheViewImpl {
        return cacheData.computeIfAbsent(shortHash) {
            InteropIdentityCacheViewImpl(shortHash)
        }
    }

    fun putInteropIdentity(holdingIdentityShortHash: String, identity: InteropIdentity) {
        getOrCreateView(holdingIdentityShortHash).putInteropIdentity(identity)
    }

    fun removeInteropIdentity(holdingIdentityShortHash: String, identity: InteropIdentity) {
        getOrCreateView(holdingIdentityShortHash).removeInteropIdentity(identity)
    }

    override fun getVirtualNodeCacheView(shortHash: String): InteropIdentityCacheView {
        return getOrCreateView(shortHash)
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.info("Component starting")
        coordinator.start()
    }

    override fun stop() {
        log.info("Component stopping")
        coordinator.stop()
    }
}
