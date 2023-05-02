package net.corda.flow.fiber.cache.impl

import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.fiber.cache.FlowFiberCacheEvictionService
import net.corda.flow.fiber.cache.FlowFiberCacheKey
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [FlowFiberCacheEvictionService::class])
class FlowFiberCacheEvictionServiceImpl @Activate constructor(
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache
) : FlowFiberCacheEvictionService {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun evictByHoldingIdentity(holdingIdentity: HoldingIdentity) {
        logger.info("Flow fiber cache evicting holding identity $holdingIdentity (${holdingIdentity.shortHash})")
        flowFiberCache.remove(holdingIdentity)
    }

    override fun evict(keys: List<FlowFiberCacheKey>) {
        logger.info("Flow fiber cache evicting keys ${keys.joinToString()}")
        flowFiberCache.remove(keys)
    }
}