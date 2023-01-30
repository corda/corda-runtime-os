package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class SandboxGroupContextCacheImpl private constructor(
    override val capacity: Long,
    private val expiryQueue: ReferenceQueue<SandboxGroupContextWrapper>,
    private val toBeClosed: MutableSet<ToBeClosed>
) : SandboxGroupContextCache {
    constructor(capacity: Long)
        : this(capacity, ReferenceQueue<SandboxGroupContextWrapper>(), ConcurrentHashMap.newKeySet())

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val WAIT_MILLIS = 100L
    }

    private class ToBeClosed(
        val cacheKey: VirtualNodeContext,
        val completion: CompletableFuture<Boolean>,
        val sandboxGroupContextToClose: AutoCloseable,
        sandboxGroupContext: SandboxGroupContextWrapper,
        referenceQueue: ReferenceQueue<SandboxGroupContextWrapper>
    ) : WeakReference<SandboxGroupContextWrapper>(sandboxGroupContext, referenceQueue)

    /**
     * Wrapper around [CloseableSandboxGroupContext], solely used to keep a [WeakReference] to every instance and only
     * invoke [CloseableSandboxGroupContext.close] on cache eviction when all strong references are gone.
     */
    private class SandboxGroupContextWrapper(
        val wrappedSandboxGroupContext: CloseableSandboxGroupContext
    ) : SandboxGroupContext by wrappedSandboxGroupContext

    /**
     * Recreates the cache with [newCapacity], and keeping the same expiry queue.
     */
    override fun resize(newCapacity: Long): SandboxGroupContextCache
        = SandboxGroupContextCacheImpl(newCapacity, expiryQueue, toBeClosed)

    /**
     * Checks whether there are contexts that were evicted from the cache but haven't been closed yet, either because
     * there are still strong reference to the [SandboxGroupContextWrapper] or because the garbage collector hasn't
     * updated the [ReferenceQueue] yet. Used for testing purposes only.
     *
     * @return number of contexts to be closed.
     */
    @VisibleForTesting
    internal val evictedContextsToBeClosed: Int
        get() {
            purgeExpiryQueue()
            return toBeClosed.size
        }

    @Suppress("TooGenericExceptionCaught")
    private fun purgeExpiryQueue() {
        // Close the wrapped [CloseableSandboxGroupContext] for every [SandboxGroupContextWrapper]
        // that has already been garbage-collected.
        while (true) {
            val head = expiryQueue.poll() as? ToBeClosed ?: break
            val vnc = head.cacheKey

            if (!toBeClosed.remove(head)) {
                logger.warn("Reaped unexpected sandboxGroup context for {}", vnc)
            }

            try {
                logger.info("Closing {} sandbox for {}", vnc.sandboxGroupType, vnc.holdingIdentity.x500Name)

                head.sandboxGroupContextToClose.close()
                head.completion.complete(true)
            } catch (exception: Exception) {
                logger.warn(
                    "Error closing ${vnc.sandboxGroupType} sandbox for ${vnc.holdingIdentity.x500Name}",
                    exception
                )
                head.completion.completeExceptionally(exception)
            }
        }
    }

    private val contexts: Cache<VirtualNodeContext, SandboxGroupContextWrapper> = CacheFactoryImpl().build(
        "Sandbox-Cache",
        Caffeine.newBuilder()
            .maximumSize(capacity)
            // Add the wrapped [CloseableSandboxGroupContext] to the internal [expiryQueue],
            // so it is only closed once it's safe to do so (i.e. wrapping [SandboxGroupContextWrapper]
            // is not referenced anymore).
            .removalListener { key, context, cause ->
                purgeExpiryQueue()
                (context?.wrappedSandboxGroupContext as? AutoCloseable)?.also { autoCloseable ->
                    toBeClosed += ToBeClosed(key!!, context.completion, autoCloseable, context, expiryQueue)
                }

                logger.info(
                    "Evicting {} sandbox for {} [{}]",
                    key!!.sandboxGroupType,
                    key.holdingIdentity.x500Name,
                    cause.name
                )
            }
    )

    override fun flush(): CompletableFuture<*> {
        purgeExpiryQueue()
        val map = HashMap(contexts.asMap())
        contexts.invalidateAll(map.keys)
        contexts.cleanUp()
        return when (map.size) {
            0 -> CompletableFuture.completedFuture(true)
            1 -> map.values.first().completion
            else -> {
                @Suppress("SpreadOperator")
                CompletableFuture.allOf(*map.values.map(SandboxGroupContext::completion).toTypedArray())
            }
        }
    }

    @Throws(InterruptedException::class)
    override fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean {
        val endTime = System.nanoTime() + duration.toNanos()
        var remaining = duration.toMillis()
        while (remaining >= 0) {
            purgeExpiryQueue()
            if (completion.isDone) {
                return true
            }
            val waiting = ((endTime - System.nanoTime()) / 1000).coerceAtMost(WAIT_MILLIS)
            if (waiting <= 0) {
                break
            }
            Thread.sleep(waiting)
            remaining -= waiting
        }
        return false
    }

    override fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? {
        purgeExpiryQueue()
        return contexts.getIfPresent(virtualNodeContext)?.let { ctx ->
            contexts.invalidate(virtualNodeContext)
            contexts.cleanUp()
            ctx.completion
        }
    }

    override fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        purgeExpiryQueue()

        return contexts.get(virtualNodeContext) {
            logger.info(
                "Caching {} sandbox for {} (cache size: {})",
                virtualNodeContext.sandboxGroupType,
                virtualNodeContext.holdingIdentity.x500Name,
                contexts.estimatedSize()
            )

            SandboxGroupContextWrapper(createFunction(virtualNodeContext))
        }
    }

    override fun close() {
        purgeExpiryQueue()
        contexts.invalidateAll()
        contexts.cleanUp()
    }
}
