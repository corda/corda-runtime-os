package net.corda.ledger.utxo.flow.impl.cache.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.ledger.utxo.flow.impl.cache.StateAndRefCache
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.SandboxedCache
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [StateAndRefCache::class])
class StateAndRefCacheImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = CacheEviction::class)
    private val cacheEviction: CacheEviction
) : SandboxedCache, StateAndRefCache {

    // TODO Access configuration to setup the cache
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(StateAndRefCacheImpl::class.java)
        private const val STATE_AND_REF_CACHE_SIZE_PROPERTY_NAME = "net.corda.ledger.utxo.flow.stateandref.cache.maximumSize"
    }

    private val maximumSize = java.lang.Long.getLong(STATE_AND_REF_CACHE_SIZE_PROPERTY_NAME, 10000)

    private val cache: Cache<SandboxedCache.CacheKey<StateRef>, StateAndRef<*>> = CacheFactoryImpl().build(
        "State-And-Ref-Cache",
        Caffeine.newBuilder().maximumSize(maximumSize)
    )

    init {
        if (!cacheEviction.addEvictionListener(SandboxGroupType.FLOW, ::onEviction)) {
            log.error("FAILED TO ADD EVICTION LISTENER")
        }
    }

    override fun get(stateRefs: Set<StateRef>): Map<StateRef, StateAndRef<*>> {
        return if (stateRefs.isNotEmpty()) {
            val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
            cache.getAllPresent(stateRefs.map {
                SandboxedCache.CacheKey(virtualNodeContext, it)
            }).map { (key, value) -> key.key to value }.toMap()
        } else {
            emptyMap()
        }
    }

    override fun putAll(stateAndRefs: List<StateAndRef<*>>) {
        if (stateAndRefs.isNotEmpty()) {
            val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
            cache.putAll(stateAndRefs.associateBy {
                SandboxedCache.CacheKey(virtualNodeContext, it.ref)
            })
        }
    }

    override fun remove(virtualNodeContext: VirtualNodeContext) {
        cache.invalidateAll(cache.asMap().keys.filter { it.virtualNodeContext == virtualNodeContext })
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
        remove(vnc)
    }
}
