package net.corda.ledger.utxo.flow.impl.cache.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.ledger.utxo.data.state.LazyStateAndRefImpl
import net.corda.ledger.utxo.flow.impl.cache.StateRefCache
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.SandboxedCache
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [StateRefCache::class])
class StateRefCacheImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = CacheEviction::class)
    private val cacheEviction: CacheEviction
) : SandboxedCache, StateRefCache {

    private data class CacheValue(val lazyStateRef: LazyStateAndRefImpl<*>?)

    // TODO Access configuration to setup the cache
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(StateRefCacheImpl::class.java)
        private const val STATE_REF_CACHE_SIZE_PROPERTY_NAME = "net.corda.ledger.utxo.flow.cache.maximumSize"
    }

    private val maximumSize = java.lang.Long.getLong(STATE_REF_CACHE_SIZE_PROPERTY_NAME, 10000)

    private val cache: Cache<SandboxedCache.CacheKey<StateRef>, CacheValue> = CacheFactoryImpl().build(
        "State-Ref-Cache",
        Caffeine.newBuilder().maximumSize(maximumSize)
    )

    override fun get(stateRefs: Set<StateRef>): Map<StateRef, LazyStateAndRefImpl<*>?> {
        return if (stateRefs.isNotEmpty()) {
            val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
            cache.getAllPresent(stateRefs.map {
                SandboxedCache.CacheKey(virtualNodeContext, it)
            }).map {
                    (key, value) -> key.key to value.lazyStateRef
            }.toMap()
        } else {
            emptyMap()
        }
    }

    override fun putAll(stateRefs: Map<StateRef, LazyStateAndRefImpl<*>?>) {
        if (stateRefs.isNotEmpty()) {
            val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
            cache.putAll(stateRefs.map {
                    (key, value) -> SandboxedCache.CacheKey(virtualNodeContext, key) to CacheValue(value)
            }.toMap())
        }
    }

    override fun remove(holdingIdentity: HoldingIdentity) {
        cache.invalidateAll(cache.asMap().keys.filter { it.holdingIdentity == holdingIdentity })
        cache.cleanUp()
    }

    @Suppress("unused")
    @Deactivate
    fun shutdown() {
        if (!cacheEviction.removeEvictionListener(SandboxGroupType.FLOW, ::onEviction)) {
            log.error("FAILED TO REMOVE EVICTION LISTENER")
        }
    }

    private fun onEviction(vnc: VirtualNodeContext) {
        log.debug {
            "Evicting cached items from ${cache::class.java} with holding identity: ${vnc.holdingIdentity} and sandbox type: " +
                    SandboxGroupType.FLOW
        }
        remove(vnc.holdingIdentity)
    }
}