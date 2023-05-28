package net.corda.flow.application.crypto

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.SandboxedCache
import net.corda.sandboxgroupcontext.SandboxedCache.CacheKey
import net.corda.v5.base.annotations.Suspendable
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [MySigningKeysCache::class, SandboxedCache::class])
class MySigningKeysCacheImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext
) : MySigningKeysCache, SandboxedCache {

    private data class CacheValue(val publicKey: PublicKey?)

    // TODO Access configuration to setup the cache
    private companion object {
        private const val MY_SIGNING_KEYS_CACHE_MAX_SIZE_PROPERTY_NAME = "net.corda.flow.application.crypto.cache.maximumSize"
    }

    private val maximumSize = java.lang.Long.getLong(MY_SIGNING_KEYS_CACHE_MAX_SIZE_PROPERTY_NAME, 10000)

    private val cache: Cache<CacheKey<PublicKey>, CacheValue> = CacheFactoryImpl().build(
        "My-Signing-Key-Cache",
        Caffeine.newBuilder().maximumSize(maximumSize)
    )

    override val sandboxGroupType: SandboxGroupType
        get() = SandboxGroupType.FLOW

    @Suspendable
    override fun get(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        return if (keys.isNotEmpty()) {
            val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
            cache.getAllPresent(keys.map { CacheKey(virtualNodeContext, it) })
                .map { (key, value) -> key.key to value.publicKey }
                .toMap()
        } else {
            emptyMap()
        }
    }

    override fun putAll(keys: Map<out PublicKey, PublicKey?>) {
        if (keys.isNotEmpty()) {
            val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
            cache.putAll(keys.map { (key, value) -> CacheKey(virtualNodeContext, key) to CacheValue(value) }.toMap())
        }
    }

    override fun remove(holdingIdentity: HoldingIdentity, sandboxGroupType: SandboxGroupType) {
        cache.invalidateAll(cache.asMap().keys.filter { it.holdingIdentity == holdingIdentity })
        cache.cleanUp()
    }
}