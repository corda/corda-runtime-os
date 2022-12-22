package net.corda.sandboxgroupcontext.impl

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCacheImpl
import net.corda.test.util.eventually
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.time.Duration.ofSeconds

class SandboxGroupContextCacheTest {
    private companion object {
        private const val TIMEOUT = 60L
    }
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
            cpkFileChecksums = setOf(SecureHash.parse("SHA-256:1234567890"))
        )
    }

    @Test
    fun `when cache is full, evict and do not close evicted sandbox if still in use`() {
        val cache = SandboxGroupContextCacheImpl(1)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()
        val contextStrongRef = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(contextStrongRef).isNotNull

        @Suppress("UnusedPrivateMember")
        for (i in 1..50) {
            cache.get(mock {
                on { holdingIdentity } doReturn idBob
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }

            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
        }

        verify(sandboxContext1, never()).close()
        assertThat(cache.evictedContextsToBeClosed).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `when cache is full, evict and close evicted sandbox if not in use anymore`() {
        val cache = SandboxGroupContextCacheImpl(1)
        val sandboxContext1: CloseableSandboxGroupContext = spy()
        var contextStrongRef: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(contextStrongRef).isNotNull

        // Trigger some evictions, close should not be invoked (there's at least one strong reference to the context)
        @Suppress("UnusedPrivateMember")
        for (i in 1..50) {
            cache.get(mock {
                on { holdingIdentity } doReturn idBob
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }
        }
        verify(sandboxContext1, never()).close()

        // Remove the strong reference to the context, so it can be garbage collected
        @Suppress("unused_value")
        contextStrongRef = null

        // Trigger some more garbage collections and cache evictions, close should be invoked now (there are no strong
        // references to the wrapper and the Garbage Collector should eventually update the internal [ReferenceQueue])
        eventually(duration = ofSeconds(TIMEOUT), waitBetween = ofSeconds(1)) {
            // Trigger Garbage Collection so the internal [ReferenceQueue] is updated
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()

            // Trigger another Cache Eviction to force the internal purge
            cache.get(mock {
                on { holdingIdentity } doReturn idBob
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }

            // Check that the evicted SandBoxGroup has been closed
            verify(sandboxContext1).close()
        }
    }

    @Suppress("unused_variable", "unused_value")
    @Test
    fun `when cache flushed, close everything`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val vNodeContext2 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn idBob
        }
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()
        val sandboxContext2 = mock<CloseableSandboxGroupContext>()

        var ref1: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(ref1).isNotNull
        var ref2: SandboxGroupContext? = cache.get(vNodeContext2) { sandboxContext2 }
        assertThat(ref2).isNotNull

        cache.flush()

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(2)
        }

        ref1 = null
        ref2 = null
        @Suppress("ExplicitGarbageCollectionCall")
        System.gc()

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        }

        verify(sandboxContext1).close()
        verify(sandboxContext2).close()
    }

    @Suppress("unused_value")
    @Test
    fun `when remove also close`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        var ref: SandboxGroupContext? = cache.get(vNodeContext1) { sandboxContext1 }
        assertThat(ref).isNotNull
        cache.remove(vNodeContext1)

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(1)
        }

        ref = null
        @Suppress("ExplicitGarbageCollectionCall")
        System.gc()

        eventually(duration = ofSeconds(TIMEOUT)) {
            assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        }

        verify(sandboxContext1).close()
    }

    @Test
    fun `when in cache retrieve same`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val sandboxContext1 = cache.get(vNodeContext1) { mock(name = "ctx1") }
        val retrievedContext = cache.get(vNodeContext1) { mock(name = "ctx2") }

        assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        assertThat(retrievedContext).isSameAs(sandboxContext1)
    }

    @Test
    fun `when key in cache equal, don't replace`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val vnodeContext1 = VirtualNodeContext(
            idAlice,
            setOf(SecureHash.parse("DUMMY:1234567890abcdef")),
            SandboxGroupType.FLOW,
            "filter"
        )
        val equalVnodeContext1 = VirtualNodeContext(
            idAlice,
            setOf(SecureHash.parse("DUMMY:1234567890abcdef")),
            SandboxGroupType.FLOW,
            "filter"
        )

        val sandboxContext1 = cache.get(vnodeContext1) { mock(name = "ctx1") }
        val retrievedContext = cache.get(equalVnodeContext1) { mock(name = "ctx2") }

        assertThat(cache.evictedContextsToBeClosed).isEqualTo(0)
        assertThat(retrievedContext).isSameAs(sandboxContext1)
    }
}
