package net.corda.flow.application.crypto

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.SandboxedCache
import net.corda.sandboxgroupcontext.SandboxedCache.CacheKey
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.CompositeKey
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.time.Duration

@Component(service = [MySigningKeysCache::class, SandboxedCache::class])
class MySigningKeysCacheImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
) : MySigningKeysCache, SandboxedCache {

    private data class CacheValue(val publicKey: PublicKey?)

    // TODO Access configuration to setup the cache
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(MySigningKeysCacheImpl::class.java)
        private const val MY_SIGNING_KEYS_CACHE_MAX_SIZE_PROPERTY_NAME = "net.corda.flow.application.crypto.cache.maximumSize"
        private const val MY_SIGNING_KEYS_EXPIRE_AFTER_WRITE_SECONDS_PROPERTY_NAME =
            "net.corda.flow.application.crypto.cache.expireAfterWriteSeconds"
    }

    private val maximumSize = java.lang.Long.getLong(MY_SIGNING_KEYS_CACHE_MAX_SIZE_PROPERTY_NAME, 10000)
    private val expireAfterWriteSeconds = java.lang.Long.getLong(MY_SIGNING_KEYS_EXPIRE_AFTER_WRITE_SECONDS_PROPERTY_NAME, 600)

    private val cache: Cache<CacheKey<PublicKey>, CacheValue> = CacheFactoryImpl().build(
        "Signing-Key-Cache",
        Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofSeconds(expireAfterWriteSeconds))
            .maximumSize(maximumSize)
    )

    @Suspendable
    override fun get(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        val virtualNodeContext = currentSandboxGroupContext.get().virtualNodeContext
        val cachedKeys = cache.getAllPresent(keys.map { CacheKey(virtualNodeContext, it) })
            .map { (key, value) -> key.key to value.publicKey }
            .toMap()

        if (cachedKeys.size == keys.size) {
            return cachedKeys
        }

        val keysToFind = keys - cachedKeys.keys

        val compositeKeys: Set<CompositeKey> = keysToFind.filterIsInstanceTo(linkedSetOf(), CompositeKey::class.java)
        val plainKeys = keysToFind - compositeKeys
        val compositeKeysLeaves: Set<PublicKey> = compositeKeys.flatMapTo(linkedSetOf()) {
            it.leafKeys
        }
        val foundSigningKeys = externalEventExecutor.execute(
            FilterMyKeysExternalEventFactory::class.java,
            plainKeys + compositeKeysLeaves
        ).toSet()

        val plainKeysReqResp = plainKeys.associateWith {
            if (it in foundSigningKeys) {
                it
            } else
                null
        }

        // TODO For now we are going to be matching composite key request with first leaf found ignoring other found leaves
        //  Perhaps we should revisit this behavior in the future.
        val compositeKeysReqResp = compositeKeys.associateWith {
            var foundLeaf: PublicKey? = null
            it.leafKeys.forEach { leaf ->
                if (leaf in foundSigningKeys) {
                    if (foundLeaf == null) {
                        foundLeaf = leaf
                    } else {
                        log.info(
                            "Found multiple composite key leaves to be owned for the same composite key by the same node " +
                                    "while there should only be one per composite key per node. " +
                                    "Composite key: \"$it\" " +
                                    "Will make use of firstly found leaf: \"$foundLeaf\" " +
                                    "Will ignore also found leaf: \"$leaf\""
                        )
                    }
                }
            }
            foundLeaf
        }

        putAll(virtualNodeContext, plainKeysReqResp)
        putAll(virtualNodeContext, compositeKeysReqResp)

        return cachedKeys + plainKeysReqResp + compositeKeysReqResp
    }

    override fun remove(holdingIdentity: HoldingIdentity, sandboxGroupType: SandboxGroupType) {
        cache.invalidateAll(cache.asMap().keys.filter { it.holdingIdentity == holdingIdentity })
        cache.cleanUp()
    }

    private fun putAll(virtualNodeContext: VirtualNodeContext, keys: Map<out PublicKey, PublicKey?>) {
        cache.putAll(keys.map { (key, value) -> CacheKey(virtualNodeContext, key) to CacheValue(value) }.toMap())
    }
}