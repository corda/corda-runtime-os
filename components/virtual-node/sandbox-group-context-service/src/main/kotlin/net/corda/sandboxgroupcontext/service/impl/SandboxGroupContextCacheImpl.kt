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
        sandboxGroupContext: SandboxGroupContextWrapper,
        referenceQueue: ReferenceQueue<SandboxGroupContextWrapper>
    ) : WeakReference<SandboxGroupContextWrapper>(sandboxGroupContext, referenceQueue)

    @VisibleForTesting
    val toBeClosed: ConcurrentHashMap.KeySetView<ToBeClosed, Boolean> = ConcurrentHashMap.newKeySet()

    private val expiryQueue = ReferenceQueue<SandboxGroupContextWrapper>()

    @Suppress("TooGenericExceptionCaught")
    private fun purgeExpiryQueue() {
        // Close the wrapped [CloseableSandboxGroupContext] for every [SandboxGroupContextWrapper]
        // that has already been garbage-collected.
        while (true) {
            val head = expiryQueue.poll() as? ToBeClosed ?: break

            if (!toBeClosed.remove(head)) {
                logger.warn("Reaped unexpected sandboxGroup context for ${head.cacheKey}")
            }

            try {
                logger.info(
                    "Closing ${head.cacheKey.sandboxGroupType} sandbox for " +
                            "${head.cacheKey.holdingIdentity.x500Name} [UNUSED]"
                )
                head.sandboxGroupContextToClose.close()
            } catch (e: Exception) {
                logger.warn(
                    "Error closing ${head.cacheKey.sandboxGroupType} sandbox for " +
                            "${head.cacheKey.holdingIdentity.x500Name}", e
                )
            }
        }
    }

    private val contexts: Cache<VirtualNodeContext, SandboxGroupContextWrapper> = CacheFactoryImpl().build(
        "Sandbox-Cache",
        Caffeine.newBuilder()
            .maximumSize(capacity)
            // If the entry was manually removed from the cache by the user, automatically close the wrapped
            // [CloseableSandboxGroupContext]. If the entry was automatically removed due to eviction, however, add the
            // wrapped [CloseableSandboxGroupContext] to the internal [expiryQueue], so it is only closed once it's safe
            // to do so (wrapping [SandboxGroupContextWrapper] is not referenced anymore).
            .removalListener { key, context, cause ->
                if (cause.wasEvicted()) {
                    (context?.wrappedSandboxGroupContext as? AutoCloseable)?.also { autoCloseable ->
                        toBeClosed += ToBeClosed(key!!, autoCloseable, context, expiryQueue)
                    }

                    logger.info(
                        "Evicting ${key!!.sandboxGroupType} sandbox for " +
                                "${key.holdingIdentity.x500Name} [${cause.name}]"
                    )

                    purgeExpiryQueue()
                } else {
                    logger.info(
                        "Closing ${key!!.sandboxGroupType} sandbox for " +
                                "${key.holdingIdentity.x500Name} [${cause.name}]"
                    )

                    (context?.wrappedSandboxGroupContext as? AutoCloseable)?.close()
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
            logger.info(
                "Caching ${virtualNodeContext.sandboxGroupType} sandbox for " +
                        "${virtualNodeContext.holdingIdentity.x500Name} (cache size: ${contexts.estimatedSize()})"
            )

            SandboxGroupContextWrapper(createFunction(virtualNodeContext))
        }
    }

    override fun close() {
        // close everything in cache
        contexts.invalidateAll()
        contexts.cleanUp()
    }

    /**
     * Wrapper around [CloseableSandboxGroupContext], solely used to keep a [WeakReference] to every instance and only
     * invoke [CloseableSandboxGroupContext.close] on cache eviction when all strong references are gone.
     */
    internal class SandboxGroupContextWrapper(
        val wrappedSandboxGroupContext: CloseableSandboxGroupContext
    ) : SandboxGroupContext, AutoCloseable {

        override fun <T : Any> get(key: String, valueType: Class<out T>) =
            wrappedSandboxGroupContext.get(key, valueType)

        override fun close() = wrappedSandboxGroupContext.close()

        override val sandboxGroup = wrappedSandboxGroupContext.sandboxGroup

        override val virtualNodeContext: VirtualNodeContext = wrappedSandboxGroupContext.virtualNodeContext
    }
}
