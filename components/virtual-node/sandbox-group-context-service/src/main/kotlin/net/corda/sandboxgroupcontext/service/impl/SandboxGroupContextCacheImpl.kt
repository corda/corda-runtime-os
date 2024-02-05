package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.EvictionListener
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class SandboxGroupContextCacheImpl private constructor(
    override val capacities: MutableMap<SandboxGroupType, Long>,
    private val evictionListeners: Map<SandboxGroupType, MutableSet<EvictionListener>>,
    private val expiryQueue: ReferenceQueue<SandboxGroupContextWrapper>,
    private val toBeClosed: MutableSet<ToBeClosed>
) : SandboxGroupContextCache {
    constructor(capacities: Long) : this(
        capacities = SandboxGroupType.values().associateWith { capacities }.toMutableMap(),
        evictionListeners = SandboxGroupType.values().associateWith { linkedSetOf<EvictionListener>() },
        expiryQueue = ReferenceQueue<SandboxGroupContextWrapper>(),
        toBeClosed = ConcurrentHashMap.newKeySet()
    )

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val WAIT_MILLIS = 100L
    }

    private fun onEviction(vnc: VirtualNodeContext) {
        val listeners = evictionListeners[vnc.sandboxGroupType] ?: return
        synchronized(listeners) {
            listeners.toList()
        }.forEach { listener ->
            try {
                listener.onEviction(vnc)
            } catch (e: Exception) {
                logger.warn("Error while evicting sandbox $vnc", e)
            }
        }
    }

    override fun addEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean {
        val listeners = evictionListeners[type] ?: return false
        return synchronized(listeners) {
            listeners.add(listener)
        }
    }

    override fun removeEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean {
        val listeners = evictionListeners[type] ?: return false
        return synchronized(listeners) {
            listeners.remove(listener)
        }
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
     * Builds a cache for the specified SandboxGroup [type] with [capacity] maximum size.
     * Uses buildNonAsync in order that the removal listener is called in the same thread as cache interactions.
     * The removal listener interacts with both the expiryQueue and the toBeClosed list, neither of which are
     * thread safe. This also ensures if purgeExpiryQueue closes any sandboxes they are closed in the same thread
     * as would be the case with all other calls to the same method.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun buildSandboxGroupTypeCache(
        type: SandboxGroupType,
        capacityIn: Long
    ): Cache<VirtualNodeContext, SandboxGroupContextWrapper>  {
        val capacity = 1000L
        logger.info("Set sandbox cache capacity to $capacity")

        return CacheFactoryImpl().buildNonAsync(
            "sandbox-cache-${type}",
            Caffeine.newBuilder()
                .maximumSize(capacity)
                // Add the wrapped [CloseableSandboxGroupContext] to the internal [expiryQueue],
                // so it is only closed once it's safe to do so (i.e. wrapping [SandboxGroupContextWrapper]
                // is not referenced anymore).
                .removalListener { key, context, cause ->
                    purgeExpiryQueue()
                    key ?: return@removalListener
                    (context?.wrappedSandboxGroupContext as? AutoCloseable)?.also { autoCloseable ->
                        toBeClosed += ToBeClosed(key, context.completion, autoCloseable, context, expiryQueue)
                        onEviction(key)
                    }

                    logger.info(
                        "Evicting {} sandbox for {} holdingId {} [{}]",
                        key.sandboxGroupType,
                        key.holdingIdentity.x500Name,
                        key.holdingIdentity.shortHash,
                        cause.name
                    )
                })

    }



    /**
     * Creates the cache for the given [sandboxGroupType] with [newCapacity] maximum size, if not created yet.
     * Changes the maximum size for the [sandboxGroupType]'s cache to [newCapacity] if the cache already exists.
     */
    @Suppress("UNUSED_PARAMETER")

    override fun resize(sandboxGroupType: SandboxGroupType, newCapacity: Long) {
        logger.info("resize sandbox cache to $newCapacity IGNORED")
//        val sandboxCache = caches.computeIfAbsent(sandboxGroupType) { type ->
//            buildSandboxGroupTypeCache(type, newCapacity)
//        }
//
//        sandboxCache.policy().eviction().ifPresent {
//            it.maximum = newCapacity
//        }
//        capacities[sandboxGroupType] = newCapacity
    }

    /**
     * Checks whether there are contexts that were evicted from the cache but haven't been closed yet, either because
     * there are still strong reference to the [SandboxGroupContextWrapper] or because the garbage collector hasn't
     * updated the [ReferenceQueue] yet. Used for testing purposes only.
     *
     * @return number of contexts to be closed.
     */
    internal val evictedContextsToBeClosed: Int
        @VisibleForTesting
        get() {
            purgeExpiryQueue()
            return toBeClosed.size
        }

    @Suppress("TooGenericExceptionCaught")
    private fun purgeExpiryQueue() {
        // Close the wrapped [CloseableSandboxGroupContext] for every [SandboxGroupContextWrapper]
        // that has already been garbage-collected.
        val start = System.nanoTime()
        logger.info("purgeExpiryQuery ${Thread.currentThread().id} starting toBeClosed=${toBeClosed.size}")
        var count = 0
        var closes = 0
        var errors = 0
        while (true) {
            count++

            logger.info("purgeExpiryQuery  ${Thread.currentThread().id} loop iteration $count queue size is ${toBeClosed.size}")
            val head = expiryQueue.poll() as? ToBeClosed ?: break
            val vnc = head.cacheKey

            if (!toBeClosed.remove(head)) {
                logger.warn("Reaped unexpected sandboxGroup context for {}", vnc)
            }

            try {
                logger.info("Closing {} sandbox for {}", vnc.sandboxGroupType, vnc.holdingIdentity.x500Name)

                head.sandboxGroupContextToClose.close()
                head.completion.complete(true)
                closes++
            } catch (exception: Exception) {
                logger.warn(
                    "Error closing ${vnc.sandboxGroupType} sandbox for ${vnc.holdingIdentity.x500Name}",
                    exception
                )
                head.completion.completeExceptionally(exception)
                errors++
            }
        }
        logger.info("purgeExpiryQuery ${Thread.currentThread().id} finished after iterations=$count closed=$closes errors=$errors in ${(System.nanoTime()-start)/1.0e6}ms")

    }

    private val caches: ConcurrentMap<SandboxGroupType, Cache<VirtualNodeContext, SandboxGroupContextWrapper>> =
        capacities.mapValuesTo(ConcurrentHashMap()) { (type, capacity) ->
            buildSandboxGroupTypeCache(type, capacity)
        }

    override fun flush(): CompletableFuture<*> {
        purgeExpiryQueue()

        val map = mutableMapOf<VirtualNodeContext, SandboxGroupContextWrapper>()
        caches.values.forEach {
            val tmp = HashMap(it.asMap())
            it.invalidateAll(tmp.keys)
            it.cleanUp()

            map.putAll(tmp)
        }

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

        val sandboxCache = caches[virtualNodeContext.sandboxGroupType]
        return sandboxCache?.getIfPresent(virtualNodeContext)?.let { ctx ->
            sandboxCache.invalidate(virtualNodeContext)
            sandboxCache.cleanUp()
            ctx.completion
        }
    }

    override fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        purgeExpiryQueue()

        val sandboxCache = caches.computeIfAbsent(virtualNodeContext.sandboxGroupType) { sandboxGroupType ->
            buildSandboxGroupTypeCache(
                sandboxGroupType,
                capacities.forSandboxGroupType(sandboxGroupType)
            )
        }

        return sandboxCache.get(virtualNodeContext) {
            logger.info(
                "Creating {} sandbox for {}",
                virtualNodeContext.sandboxGroupType,
                virtualNodeContext.holdingIdentity.x500Name
            )
            SandboxGroupContextWrapper(createFunction(virtualNodeContext))
        }
    }

    override fun close() {
        purgeExpiryQueue()

        caches.values.forEach {
            it.invalidateAll()
            it.cleanUp()
        }
    }

    private fun Map<SandboxGroupType, Long>.forSandboxGroupType(type: SandboxGroupType) =
        this[type] ?: // we should never actually get here. If we do it's a problem in the creation of the map
        throw CordaRuntimeException("An invalid sandbox group type ($type) has been used in the ${this::class.java.name}")
}
