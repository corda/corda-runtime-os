package net.corda.sandboxgroupcontext.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.metrics.CordaMetrics
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.EvictionListener
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCacheImpl
import net.corda.test.util.eventually
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.metrics.EachTestCordaMetrics
import net.corda.test.util.metrics.CORDA_METRICS_LOCK
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration.ofSeconds
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

@Suppress("ExplicitGarbageCollectionCall")
@ResourceLock(CORDA_METRICS_LOCK)
class SandboxGroupContextCacheTest {
    private companion object {
        private const val TIMEOUT = 60L

        private fun createRandomFilter() = "(uuid=${UUID.randomUUID()})"
        private fun mockSandboxContext(name: String? = null): CloseableSandboxGroupContext = mock(name = name) {
            val completable = CompletableFuture<Boolean>()
            whenever(it.completion).doReturn(completable)
        }
    }

    @Suppress("unused")
    @RegisterExtension
    private val metrics = EachTestCordaMetrics("Testing Sandboxes")

    private lateinit var idBob: HoldingIdentity
    private lateinit var idAlice: HoldingIdentity
    private lateinit var vNodeContext1: VirtualNodeContext

    @BeforeEach
    fun setUp() {
        idBob = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        idAlice = createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group")

        vNodeContext1 = VirtualNodeContext(
            holdingIdentity = idBob,
            serviceFilter = "filter",
            sandboxGroupType = SandboxGroupType.FLOW,
            cpkFileChecksums = setOf(parseSecureHash("SHA-256:1234567890"))
        )
    }

    @Test
    fun `test adding eviction listeners`() {
        val listener = mock<EvictionListener>()
        val cache = SandboxGroupContextCacheImpl(1)

        // We can add the same listener ONCE for each sandbox group type.
        for (type in SandboxGroupType.values()) {
            assertTrue(cache.addEvictionListener(type, listener))
            assertFalse(cache.addEvictionListener(type, listener))
        }

        // And we can remove that listener again afterwards.
        for (type in SandboxGroupType.values()) {
            assertTrue(cache.removeEvictionListener(type, listener))
            assertFalse(cache.removeEvictionListener(type, listener))
        }

        // Adding and removing doesn't invoke the listener (obviously).
        verify(listener, never()).onEviction(any())
    }

    @Test
    fun `when cache is full, evict and do not close evicted sandbox if still in use`() {
        val count = 50
        val listener = mock<EvictionListener>()
        val cache = SandboxGroupContextCacheImpl(1).apply {
            assertTrue(addEvictionListener(vNodeContext1.sandboxGroupType, listener))
        }
        val sandboxContext1 = mockSandboxContext()
        val contextStrongRef = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(contextStrongRef).isNotNull

        @Suppress("UnusedPrivateMember")
        for (i in 0 until count) {
            cache.get(VirtualNodeContext(
                holdingIdentity = createTestHoldingIdentity("CN=Bob-$i, O=Bob Corp, L=LDN, C=GB", "group"),
                cpkFileChecksums = emptySet(),
                sandboxGroupType = SandboxGroupType.FLOW,
                serviceFilter = createRandomFilter()
            )) { mockSandboxContext() }

            System.gc()
        }

        verify(sandboxContext1, never()).close()
        assertThat(cache.evictedContextsToBeClosed).isGreaterThanOrEqualTo(1)

        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = count + 1, misses = count + 1, evictions = count)
            verify(listener, times(count)).onEviction(any())
        }
    }

    @Test
    fun `when cache is full, evict and close evicted sandbox if not in use anymore`() {
        val count = 25
        val cache = SandboxGroupContextCacheImpl(1)
        val sandboxContext1: CloseableSandboxGroupContext = spy {
            val completable = CompletableFuture<Boolean>()
            whenever(it.completion).doReturn(completable)
        }
        var contextStrongRef: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(contextStrongRef).isNotNull

        // Trigger some evictions, close should not be invoked (there's at least one strong reference to the context)
        @Suppress("UnusedPrivateMember")
        for (i in 0 until count) {
            cache.get(VirtualNodeContext(
                holdingIdentity = createTestHoldingIdentity("CN=Bob-$i, O=Bob Corp, L=LDN, C=GB", "group"),
                cpkFileChecksums = emptySet(),
                sandboxGroupType = SandboxGroupType.FLOW,
                serviceFilter = createRandomFilter()
            )) { mockSandboxContext() }
        }
        verify(sandboxContext1, never()).close()

        // Remove the strong reference to the context, so it can be garbage collected
        @Suppress("unused_value")
        contextStrongRef = null

        // Trigger some more garbage collections and cache evictions, close should be invoked now (there are no strong
        // references to the wrapper and the Garbage Collector should eventually update the internal [ReferenceQueue])
        var extraOps = 0
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            // Trigger Garbage Collection so the internal [ReferenceQueue] is updated
            System.gc()

            // Trigger another Cache Eviction to force the internal purge
            cache.get(
                VirtualNodeContext(
                    holdingIdentity = createTestHoldingIdentity(
                        "CN=Alice-$extraOps, O=Alice Corp, L=LDN, C=GB",
                        "group"
                    ),
                    cpkFileChecksums = emptySet(),
                    sandboxGroupType = SandboxGroupType.FLOW,
                    serviceFilter = createRandomFilter()
                )
            ) { mockSandboxContext() }
            extraOps++

            // Check that the evicted SandBoxGroup has been closed
            verify(sandboxContext1).close()
        }

        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = count + 1 + extraOps, misses = count + 1 + extraOps, evictions = count + extraOps)
        }
    }

    @Suppress("unused_value")
    @Test
    fun `when cache closed, close everything`() {
        val listener = mock<EvictionListener>()
        val cache = SandboxGroupContextCacheImpl(10).apply {
            assertTrue(addEvictionListener(SandboxGroupType.FLOW, listener))
        }
        val vNodeContext2 = VirtualNodeContext(
            holdingIdentity = idBob,
            cpkFileChecksums = setOf(parseSecureHash("DUMMY:1234567890abcdef")),
            sandboxGroupType = SandboxGroupType.FLOW,
            serviceFilter = createRandomFilter()
        )
        val sandboxContext1 = mockSandboxContext()
        val sandboxContext2 = mockSandboxContext()

        var ref1: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(ref1).isNotNull
        var ref2: SandboxGroupContext? = cache.get(vNodeContext2) { sandboxContext2 }
        assertThat(ref2).isNotNull

        verify(listener, never()).onEviction(any())
        cache.close()

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(2)
            verify(listener).onEviction(vNodeContext1)
            verify(listener).onEviction(vNodeContext2)
        }

        ref1 = null
        ref2 = null

        eventually(duration = ofSeconds(TIMEOUT)) {
            System.gc()
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        }

        verify(sandboxContext1).close()
        verify(sandboxContext2).close()
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = 2, misses = 2)
        }
    }

    @Test
    fun `when remove also close`() {
        val listener = mock<EvictionListener>()
        val cache = SandboxGroupContextCacheImpl(1).apply {
            assertTrue(addEvictionListener(vNodeContext1.sandboxGroupType, listener))
        }
        val sandboxContext1 = mockSandboxContext()

        var ref: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(ref).isNotNull

        assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        val completion = cache.remove(vNodeContext1.copy())
            ?: fail("No sandbox for $vNodeContext1")

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(1)
            verify(listener).onEviction(eq(vNodeContext1))
        }
        assertThat(completion.isDone).isFalse

        @Suppress("unused_value")
        ref = null

        eventually(duration = ofSeconds(TIMEOUT)) {
            System.gc()
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
            assertThat(completion.isDone).isTrue
        }

        verify(sandboxContext1).close()
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = 1, hits = 1, misses = 1)
        }
    }

    @Test
    fun removingMissingKeyReturnsNull() {
        val listener = mock<EvictionListener>()
        val cache = SandboxGroupContextCacheImpl(10).apply {
            assertTrue(addEvictionListener(vNodeContext1.sandboxGroupType, listener))
        }
        assertThat(cache.remove(vNodeContext1)).isNull()
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(misses = 1)
        }
        verify(listener, never()).onEviction(any())
    }

    @Test
    fun `when in cache retrieve same`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val sandboxContext1 = cache.get(vNodeContext1) { mockSandboxContext(name = "ctx1") }
        val retrievedContext = cache.get(vNodeContext1) { mockSandboxContext(name = "ctx2") }

        assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        assertThat(retrievedContext).isSameAs(sandboxContext1)
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = 1, hits = 1, misses = 1)
        }
    }

    @Test
    fun `when key in cache equal, don't replace`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val vnodeContext1 = VirtualNodeContext(
            idAlice,
            setOf(parseSecureHash("DUMMY:1234567890abcdef")),
            SandboxGroupType.FLOW,
            "filter"
        )
        val equalVnodeContext1 = VirtualNodeContext(
            idAlice,
            setOf(parseSecureHash("DUMMY:1234567890abcdef")),
            SandboxGroupType.FLOW,
            "filter"
        )

        val sandboxContext1 = cache.get(vnodeContext1) { mockSandboxContext(name = "ctx1") }
        val retrievedContext = cache.get(equalVnodeContext1) { mockSandboxContext(name = "ctx2") }

        assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        assertThat(retrievedContext).isSameAs(sandboxContext1)
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = 1, hits = 1, misses = 1)
        }
    }

    @Test
    fun testEmptyCacheFlushesImmediately() {
        val cache = SandboxGroupContextCacheImpl(10)
        val completion = cache.flush()
        assertThat(completion.isDone).isTrue
        assertThat(cache.waitFor(completion, ofSeconds(0))).isTrue
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics()
        }
    }

    @Suppress("unused_value")
    @Test
    fun testCacheFlushesCurrentContents() {
        val listener = mock<EvictionListener>()
        val cache = SandboxGroupContextCacheImpl(10).apply {
            assertTrue(addEvictionListener(SandboxGroupType.FLOW, listener))
        }
        val vNodeContext2 = VirtualNodeContext(
            holdingIdentity = idAlice,
            cpkFileChecksums = emptySet(),
            sandboxGroupType = SandboxGroupType.FLOW,
            serviceFilter = createRandomFilter()
        )
        val vNodeContext3 = VirtualNodeContext(
            holdingIdentity = idBob,
            cpkFileChecksums = emptySet(),
            sandboxGroupType = SandboxGroupType.FLOW,
            serviceFilter = createRandomFilter()
        )

        val sandboxContext1 = mockSandboxContext()
        val sandboxContext2 = mockSandboxContext()
        val sandboxContext3 = mockSandboxContext()

        var ref1: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        var ref2: SandboxGroupContext? = cache.get(vNodeContext2) { sandboxContext2 }
        assertThat(ref1).isNotNull
        assertThat(ref2).isNotNull
        verify(listener, never()).onEviction(any())

        val completion = cache.flush()
        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(2)
            verify(listener).onEviction(vNodeContext1)
            verify(listener).onEviction(vNodeContext2)
        }
        assertThat(completion.isDone).isFalse

        cache.get(vNodeContext3) { sandboxContext3 }

        ref1 = null
        ref2 = null

        eventually(duration = ofSeconds(TIMEOUT)) {
            System.gc()
            assertThat(cache.waitFor(completion, ofSeconds(1))).isTrue
            assertThat(completion.isDone).isTrue
        }
        assertThat(sandboxContext1.completion.isDone).isTrue
        assertThat(sandboxContext2.completion.isDone).isTrue
        assertThat(sandboxContext3.completion.isDone).isFalse
        verify(sandboxContext1).close()
        verify(sandboxContext2).close()
        verify(sandboxContext3, never()).close()
        verify(listener, times(2)).onEviction(any())
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = 3, misses = 3)
        }
    }

    @Suppress("unused_value")
    @Test
    fun testFlushingSandboxWithExceptionOnClose() {
        val cache = SandboxGroupContextCacheImpl(10)

        val sandboxContext1 = mock<CloseableSandboxGroupContext> {
            val completable = CompletableFuture<Boolean>()
            whenever(it.completion).doReturn(completable)
            whenever(it.close()).doThrow(Exception("Failed to close sandbox"))
        }
        var ref1: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(ref1).isNotNull

        val completion = cache.flush()
        assertThat(completion.isDone).isFalse

        ref1 = null

        eventually(duration = ofSeconds(TIMEOUT)) {
            System.gc()
            assertThat(cache.waitFor(completion, ofSeconds(1))).isTrue
            assertThat(completion.isDone).isTrue
        }
        assertThat(sandboxContext1.completion.isCompletedExceptionally).isTrue
        verify(sandboxContext1).close()
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            System.gc()
            verifyCacheMetrics(puts = 1, misses = 1)
        }
    }

    private fun verifyCacheMetrics(
        sandboxType: SandboxGroupType = SandboxGroupType.FLOW,
        puts: Int = 0,
        hits: Int = 0,
        misses: Int = 0,
        evictions: Int = 0,
    ) {
        val cacheName = "sandbox-cache-${sandboxType}"

        val cachePuts = CordaMetrics.registry
            .find("corda.cache.puts")
            .tag("cache", cacheName).functionCounter()?.count()?.roundToInt()
        assertThat(cachePuts)
            .withFailMessage("Expected $cacheName puts from metrics to be $puts but was $cachePuts")
            .isEqualTo(puts)

        val cacheHits = CordaMetrics.registry
            .find("corda.cache.gets")
            .tags("cache", cacheName, "result", "hit").functionCounter()?.count()?.roundToInt()
        assertThat(cacheHits)
            .withFailMessage("Expected $cacheName hits from metrics to be $hits but was $cacheHits")
            .isEqualTo(hits)

        val cacheMisses = CordaMetrics.registry
            .find("corda.cache.gets")
            .tags("cache", cacheName, "result", "miss").functionCounter()?.count()?.roundToInt()
        assertThat(cacheMisses)
            .withFailMessage("Expected $cacheName misses from metrics to be $misses but was $cacheMisses")
            .isEqualTo(misses)

        val cacheEvictions = CordaMetrics.registry
            .find("corda.cache.evictions")
            .tag("cache", cacheName).functionCounter()?.count()?.roundToInt()
        assertThat(cacheEvictions)
            .withFailMessage("Expected $cacheName evictions from metrics to be $evictions but was $cacheEvictions")
            .isEqualTo(evictions)
    }
}
