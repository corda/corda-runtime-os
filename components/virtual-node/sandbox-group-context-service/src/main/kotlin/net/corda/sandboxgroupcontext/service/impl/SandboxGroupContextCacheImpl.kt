package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.loggerFor
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal class SandboxGroupContextCacheImpl(override val capacity: Long) : SandboxGroupContextCache {
    private companion object {
        private val logger = loggerFor<SandboxGroupContextCache>()
    }

    @VisibleForTesting
    class ToBeClosed(
        val cacheKey: VirtualNodeContext,
        val sandboxGroupContextToClose: AutoCloseable,
        sandboxGroupContext: CloseableSandboxGroupContext,
        referenceQueue: ReferenceQueue<CloseableSandboxGroupContext>
    ) : WeakReference<CloseableSandboxGroupContext>(sandboxGroupContext, referenceQueue)

    @VisibleForTesting
    val toBeClosed: ConcurrentHashMap.KeySetView<ToBeClosed, Boolean> = ConcurrentHashMap.newKeySet()

    private val expiryQueue = ReferenceQueue<CloseableSandboxGroupContext>()

    @Suppress("TooGenericExceptionCaught")
    private fun purgeExpiryQueue() {
        // Close contexts that have already been garbage-collected
        while (true) {
            val head = expiryQueue.poll() as? ToBeClosed ?: break
            if (!toBeClosed.remove(head)) {
                logger.warn("Reaped unexpected sandboxGroup context for ${head.cacheKey}")
            }

            try {
                head.sandboxGroupContextToClose.close()
            } catch (e: Exception) {
                logger.warn("Error closing sandboxGroup context for ${head.cacheKey}", e)
            }
        }
    }

    private val contexts: Cache<VirtualNodeContext, CloseableSandboxGroupContext> = CacheFactoryImpl().build(
        "Sandbox-Cache",
        Caffeine.newBuilder()
            .maximumSize(capacity)
            // If the entry was manually removed from the cache by the user, automatically close the relevant
            // SandboxGroupContext. If the entry was automatically removed due to eviction, however, add the
            // SandboxGroupContext to the internal expiryQueue, so it is only closed once it is not referenced anymore.
            .removalListener { key, value, cause ->
                if (cause.wasEvicted()) {
                    (value as? AutoCloseable)?.also { autoCloseable ->
                        toBeClosed += ToBeClosed(key!!, autoCloseable, value , expiryQueue)
                    }

                    logger.info(
                        "Evicting ${key!!.sandboxGroupType} sandbox for: " +
                                "${key.holdingIdentity.x500Name} [${cause.name}]"
                    )

                    purgeExpiryQueue()
                } else {
                    logger.info(
                        "Removing ${key!!.sandboxGroupType} sandbox for: " +
                                "${key.holdingIdentity.x500Name} [${cause.name}]"
                    )

                    value?.close()
                }
            }
    )

    override fun remove(virtualNodeContext: VirtualNodeContext) {
        purgeExpiryQueue()
        contexts.invalidate(virtualNodeContext)
    }

    override fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        purgeExpiryQueue()

        return contexts.get(virtualNodeContext) {
            logger.info("Caching ${virtualNodeContext.sandboxGroupType} sandbox for: " +
                    "${virtualNodeContext.holdingIdentity.x500Name} (cache size: ${contexts.estimatedSize()})")

            createFunction(virtualNodeContext)
        }
    }

    override fun close() {
        // close everything in cache
        contexts.invalidateAll()
        contexts.cleanUp()
    }
}
