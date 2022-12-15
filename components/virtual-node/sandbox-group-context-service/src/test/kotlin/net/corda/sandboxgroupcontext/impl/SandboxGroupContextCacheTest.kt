package net.corda.sandboxgroupcontext.impl

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCacheImpl
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
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class SandboxGroupContextCacheTest {
    private val timeout = 10000L
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

        cache.get(vNodeContext1) { sandboxContext1 }

        @Suppress("UnusedPrivateMember")
        for (i in 1..50) {
            cache.get(mock {
                on { holdingIdentity } doReturn idBob
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }
        }

        assertThat(cache.toBeClosed).isNotEmpty
        verify(sandboxContext1, never()).close()
    }

    @Test
    fun `when cache is full, evict and close evicted sandbox if not in use anymore`() {
        val cache = SandboxGroupContextCacheImpl(1)
        val sandboxContext1 = spy<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }

        // Trigger some evictions, close should not be invoked (there's at least one reference)
        @Suppress("UnusedPrivateMember")
        for (i in 1..50) {
            cache.get(mock {
                on { holdingIdentity } doReturn idBob
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }
        }

        // Simulate the reference enqueue done by the GC execution (it might take longer than one
        // cycle for the GC to do this, and we can't rely on System.gc())
        cache.toBeClosed.forEach {
            it.enqueue()
        }

        // Trigger some more evictions, close should be invoked now
        @Suppress("UnusedPrivateMember")
        for (i in 1..50) {
            cache.get(mock {
                on { holdingIdentity } doReturn idBob
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }
        }

        verify(sandboxContext1, timeout(timeout)).close()
    }

    @Test
    fun `when cache closed, close everything`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val vNodeContext2 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn idBob
        }
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()
        val sandboxContext2 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }
        cache.get(vNodeContext2) { sandboxContext2 }
        assertThat(cache.toBeClosed).isEmpty()

        cache.close()

        verify(sandboxContext1, timeout(timeout)).close()
        verify(sandboxContext2, timeout(timeout)).close()
    }

    @Test
    fun `when remove also close`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }
        cache.remove(vNodeContext1)
        assertThat(cache.toBeClosed).isEmpty()

        verify(sandboxContext1, timeout(timeout)).close()
    }

    @Test
    fun `when in cache retrieve same`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }
        val retrievedContext =
            cache.get(vNodeContext1) { mock() } as SandboxGroupContextCacheImpl.SandboxGroupContextWrapper

        assertThat(cache.toBeClosed).isEmpty()
        assertThat(retrievedContext.wrappedSandboxGroupContext).isSameAs(sandboxContext1)
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

        val sandboxContext1 = mock<CloseableSandboxGroupContext>()
        cache.get(vnodeContext1) { sandboxContext1 }
        val retrievedContext =
            cache.get(equalVnodeContext1) { mock() } as SandboxGroupContextCacheImpl.SandboxGroupContextWrapper

        assertThat(cache.toBeClosed).isEmpty()
        assertThat(retrievedContext.wrappedSandboxGroupContext).isSameAs(sandboxContext1)
    }
}
