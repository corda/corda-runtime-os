package net.corda.interop.aliasinfo.cache.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.interop.InteropAliasIdentity
import net.corda.interop.aliasinfo.cache.InteropIdentityCacheService
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

    private val lifecycleEventHandler = AliasInfoCacheServiceEventHandler(
        configurationReadService, subscriptionFactory, this
    )

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropIdentityCacheService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleEventHandler)

    /**
     * Outer key is the holding identity short hash.
     * Inner key is the interop group UUID.
     */
    private val cacheData = HashMap<String, HashMap<String, InteropAliasIdentity>>()

    private fun getAliasIdentityMapFor(holdingIdentityShortHash: String): HashMap<String, InteropAliasIdentity> {
        if (!cacheData.containsKey(holdingIdentityShortHash)) {
            cacheData[holdingIdentityShortHash] = HashMap()
        }

        return cacheData[holdingIdentityShortHash]!!
    }

    override fun getAliasIdentities(shortHash: String): Map<String, InteropAliasIdentity> {
        return getAliasIdentityMapFor(shortHash)
    }

    override fun putAliasIdentity(shortHash: String, aliasIdentity: InteropAliasIdentity) {
        log.info("Adding alias identity, shortHash: $shortHash, identity=$aliasIdentity")
        val identities = getAliasIdentityMapFor(shortHash)
        identities[aliasIdentity.groupId] = aliasIdentity
    }

    override fun removeAliasIdentity(shortHash: String, aliasIdentity: InteropAliasIdentity) {
        val identities = getAliasIdentityMapFor(shortHash)
        identities.remove(aliasIdentity.groupId)?.let {
            log.info("Removing alias identity, shortHash: $shortHash, identity=$it")
        }
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
