package net.corda.sandboxgroupcontext.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.metrics.CordaMetrics
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCacheImpl
import net.corda.test.util.eventually
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration.ofSeconds
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Suppress("ExplicitGarbageCollectionCall")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class SandboxGroupContextCacheTest {
    private companion object {
        private const val TIMEOUT = 60L

        private fun createRandomFilter() = "(uuid=${UUID.randomUUID()})"
        private fun mockSandboxContext(name: String? = null): CloseableSandboxGroupContext = mock(name = name) {
            val completable = CompletableFuture<Boolean>()
            whenever(it.completion).doReturn(completable)
        }
    }

    private lateinit var idBob: HoldingIdentity
    private lateinit var idAlice: HoldingIdentity
    private lateinit var vNodeContext1: VirtualNodeContext

    private fun defaultInitialCapacities(initial: Long) = SandboxGroupType.values().associateWith { initial }

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

        CordaMetrics.registry.clear()
    }

    @Test
    fun `when cache is full, evict and do not close evicted sandbox if still in use`() {
        val count = 50.0
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(1))
        val sandboxContext1 = mockSandboxContext()
        val contextStrongRef = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(contextStrongRef).isNotNull

        @Suppress("UnusedPrivateMember")
        for (i in 1..count.toInt()) {
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
            verifyCacheMetrics(puts = count + 1, misses = count + 1, evictions = count)
        }
    }

    @Test
    fun `when cache is full, evict and close evicted sandbox if not in use anymore`() {
        val count = 25.0
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(1))
        val sandboxContext1: CloseableSandboxGroupContext = spy {
            val completable = CompletableFuture<Boolean>()
            whenever(it.completion).doReturn(completable)
        }
        var contextStrongRef: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(contextStrongRef).isNotNull

        // Trigger some evictions, close should not be invoked (there's at least one strong reference to the context)
        @Suppress("UnusedPrivateMember")
        for (i in 1..count.toInt()) {
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
            verifyCacheMetrics(puts = count + 1 + extraOps, misses = count + 1 + extraOps, evictions = count + extraOps)
        }
    }

    @Suppress("unused_value")
    @Test
    fun `when cache closed, close everything`() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))
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

        cache.close()

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(2)
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
            verifyCacheMetrics(puts = 2.0, misses = 2.0)
        }
    }

    @Suppress("unused_value")
    @Test
    fun `when remove also close`() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))
        val sandboxContext1 = mockSandboxContext()

        var ref: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(ref).isNotNull
        val completion = cache.remove(vNodeContext1.copy())
            ?: fail("No sandbox for $vNodeContext1")

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(1)
            assertThat(completion.isDone).isFalse
        }

        ref = null

        eventually(duration = ofSeconds(TIMEOUT)) {
            System.gc()
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
            assertThat(completion.isDone).isTrue
        }

        verify(sandboxContext1).close()
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            verifyCacheMetrics(puts = 1.0, hits = 1.0, misses = 1.0)
        }
    }

    @Test
    fun removingMissingKeyReturnsNull() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))
        assertThat(cache.remove(vNodeContext1)).isNull()
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            verifyCacheMetrics(misses = 1.0)
        }
    }

    @Test
    fun `when in cache retrieve same`() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))
        val sandboxContext1 = cache.get(vNodeContext1) { mockSandboxContext(name = "ctx1") }
        val retrievedContext = cache.get(vNodeContext1) { mockSandboxContext(name = "ctx2") }

        assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        assertThat(retrievedContext).isSameAs(sandboxContext1)
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            verifyCacheMetrics(puts = 1.0, hits = 1.0, misses = 1.0)
        }
    }

    @Test
    fun `when key in cache equal, don't replace`() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))
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
            verifyCacheMetrics(puts = 1.0, hits = 1.0, misses = 1.0)
        }
    }

    @Test
    fun testEmptyCacheFlushesImmediately() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))
        val completion = cache.flush()
        assertThat(completion.isDone).isTrue
        assertThat(cache.waitFor(completion, ofSeconds(0))).isTrue
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            verifyCacheMetrics()
        }
    }

    @Suppress("unused_value")
    @Test
    fun testCacheFlushesCurrentContents() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))
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

        val completion = cache.flush()
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
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            verifyCacheMetrics(puts = 3.0, misses = 3.0)
        }
    }

    @Suppress("unused_value")
    @Test
    fun testFlushingSandboxWithExceptionOnClose() {
        val cache = SandboxGroupContextCacheImpl(defaultInitialCapacities(10))

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
            verifyCacheMetrics(puts = 1.0, misses = 1.0)
        }
    }

    private fun verifyCacheMetrics(
        sandboxType: SandboxGroupType = SandboxGroupType.FLOW,
        puts: Double = 0.0,
        hits: Double = 0.0,
        misses: Double = 0.0,
        evictions: Double = 0.0,
    ) {
        val cacheName = "sandbox-cache-${sandboxType}"

        val cachePuts = CordaMetrics.registry
            .find("cache.puts")
            .tags("cache", cacheName).meter()?.measure()?.first()?.value
        assertThat(cachePuts)
            .withFailMessage("Expected $cacheName puts from metrics to be $puts but was $cachePuts")
            .isEqualTo(puts)

        val cacheHits = CordaMetrics.registry
            .find("cache.gets")
            .tags("cache", cacheName, "result", "hit").meter()?.measure()?.first()?.value
        assertThat(cacheHits)
            .withFailMessage("Expected $cacheName hits from metrics to be $hits but was $cacheHits")
            .isEqualTo(hits)

        val cacheMisses = CordaMetrics.registry
            .find("cache.gets")
            .tags("cache", cacheName, "result", "miss").meter()?.measure()?.first()?.value
        assertThat(cacheMisses)
            .withFailMessage("Expected $cacheName misses from metrics to be $misses but was $cacheMisses")
            .isEqualTo(misses)

        val cacheEvictions = CordaMetrics.registry
            .find("cache.evictions")
            .tags("cache", cacheName).meter()?.measure()?.first()?.value
        assertThat(cacheEvictions)
            .withFailMessage("Expected $cacheName evictions from metrics to be $evictions but was $cacheEvictions")
            .isEqualTo(evictions)
    }
}
