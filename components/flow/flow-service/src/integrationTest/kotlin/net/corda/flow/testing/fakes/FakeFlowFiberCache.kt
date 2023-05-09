package net.corda.flow.testing.fakes

import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.cache.FlowFiberCache
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [FlowFiberCache::class, FakeFlowFiberCache::class])
class FakeFlowFiberCache @Activate constructor(
    @Reference(service = FlowFiberCache::class, target = "(component.name=flow-fiber-cache)")
    val cache: FlowFiberCache
) : FlowFiberCache {

    private var tracker = mutableMapOf<FlowKey, MutableList<FlowFiberCacheOperation>>()

    private fun track(key: FlowKey, op: FlowFiberCacheOperation) {
        tracker.merge(key, mutableListOf(op)) { old, new ->
            old.addAll(new)
            old
        }
    }

    override fun put(key: FlowKey, fiber: FlowFiberImpl) {
        track(key, FlowFiberCacheOperation.PUT)
        cache.put(key, fiber)
    }

    override fun get(key: FlowKey): FlowFiberImpl? {
        return cache.get(key)
    }

    override fun remove(key: FlowKey) {
        track(key, FlowFiberCacheOperation.REMOVE)
        cache.remove(key)
    }

    override fun remove(keys: Collection<FlowKey>) {
        keys.forEach {
            track(it, FlowFiberCacheOperation.REMOVE)
            cache.remove(it)
        }
    }

    override fun remove(holdingIdentity: HoldingIdentity) {
        tracker.keys
            .filter { it.identity == holdingIdentity }
            .forEach {
                track(it, FlowFiberCacheOperation.REMOVE)
            }
        cache.remove(holdingIdentity)
    }

    /**
     * Return list of put and remove operations at this key. Null means no operations were attempted at this key.
     */
    fun getManipulations(key: FlowKey): List<FlowFiberCacheOperation>? {
        return tracker[key]
    }

    fun reset(holdingIdentities: List<HoldingIdentity>) {
        tracker = mutableMapOf()
        holdingIdentities.forEach { cache.remove(it) }
    }
}

enum class FlowFiberCacheOperation {
    PUT, REMOVE
}
