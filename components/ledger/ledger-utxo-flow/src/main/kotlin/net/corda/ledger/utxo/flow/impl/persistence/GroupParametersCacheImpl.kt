package net.corda.ledger.utxo.flow.impl.persistence

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.SandboxedCache
import net.corda.sandboxgroupcontext.SandboxedCache.CacheKey
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.utilities.debug
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [GroupParametersCache::class])
class GroupParametersCacheImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = CacheEviction::class)
    private val cacheEviction: CacheEviction
) : GroupParametersCache, SandboxedCache {

    // TODO Access configuration to setup the cache
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(GroupParametersCacheImpl::class.java)
        private const val GROUP_PARAMETERS_CACHE_MAX_SIZE_PROPERTY_NAME =
            "net.corda.ledger.utxo.flow.impl.persistence.group.parameters.cache.maximumSize"
    }

    private val maximumSize = java.lang.Long.getLong(GROUP_PARAMETERS_CACHE_MAX_SIZE_PROPERTY_NAME, 1000)

    private val cache: Cache<CacheKey<SecureHash>, SignedGroupParameters> = CacheFactoryImpl().build(
        "Group-parameters-cache",
        Caffeine.newBuilder().maximumSize(maximumSize)
    )

    init {
        if (!cacheEviction.addEvictionListener(SandboxGroupType.FLOW, ::onEviction)) {
            log.error("FAILED TO ADD EVICTION LISTENER")
        }
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
            "Evicting cached items from ${cache::class.java} with holding identity: ${vnc.holdingIdentity}, " +
                    "cpkFileChecksums: ${vnc.cpkFileChecksums} and sandbox type: ${SandboxGroupType.FLOW}"
        }
        remove(vnc)
    }

    override fun get(hash: SecureHash): SignedGroupParameters? {
        val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
        return cache.getIfPresent(CacheKey(virtualNodeContext, hash))
    }

    override fun put(groupParameters: SignedGroupParameters) {
        val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
        cache.put(CacheKey(virtualNodeContext, groupParameters.hash), groupParameters)
    }

    override fun remove(virtualNodeContext: VirtualNodeContext) {
        cache.invalidateAll(cache.asMap().keys.filter { it.virtualNodeContext == virtualNodeContext })
        cache.cleanUp()
    }
}