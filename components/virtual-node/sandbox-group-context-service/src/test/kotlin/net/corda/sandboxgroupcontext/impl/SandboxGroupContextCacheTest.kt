package net.corda.sandboxgroupcontext.impl

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCacheImpl
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class SandboxGroupContextCacheTest {
    private lateinit var id : HoldingIdentity
    private lateinit var vNodeContext1: VirtualNodeContext

    @BeforeEach
    fun setUp() {
        id = mock {
            on { x500Name } doReturn MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB")
        }

        vNodeContext1 = mock {
            on { holdingIdentity } doReturn id
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
        }
    }

    @Test
    fun `when cache full, evict and do not close evicted if in use`() {
        val cache = SandboxGroupContextCacheImpl(1)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }

        @Suppress("UnusedPrivateMember")
        for (i in 1..100) {
            cache.get(mock {
                on { holdingIdentity } doReturn id
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }
        }

        assertThat(cache.toBeClosed).isNotEmpty
        verify(sandboxContext1, never()).close()
    }

    @Test
    fun `when cache full and not in use anymore, evict and close evicted`() {
        val cache = SandboxGroupContextCacheImpl(1)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }

        // First eviction, close should not be invoked (there's at least one reference)
        cache.get(mock {
            on { holdingIdentity } doReturn id
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
        }) { mock() }
        verify(sandboxContext1, never()).close()

        // Simulate the reference enqueue done by the GC execution (it might take longer than one
        // cycle for the GC to do this, and we can't rely on System.gc())
        cache.toBeClosed.forEach {
            it.enqueue()
        }

        @Suppress("UnusedPrivateMember")
        // Trigger some evictions, close should be invoked now
        for (i in 1..50) {
            cache.get(mock {
                on { holdingIdentity } doReturn id
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            }) { mock() }
        }

        verify(sandboxContext1, timeout(5000)).close()
    }

    @Test
    fun `when cache closed, close everything`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val vNodeContext2 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn id
        }
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()
        val sandboxContext2 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }
        cache.get(vNodeContext2) { sandboxContext2 }
        assertThat(cache.toBeClosed).isEmpty()

        cache.close()

        verify(sandboxContext1, timeout(5000)).close()
        verify(sandboxContext2, timeout(5000)).close()
    }

    @Test
    fun `when remove also close`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }
        cache.remove(vNodeContext1)
        assertThat(cache.toBeClosed).isEmpty()

        verify(sandboxContext1, timeout(5000)).close()
    }

    @Test
    fun `when in cache retrieve same`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vNodeContext1) { sandboxContext1 }
        val retrievedContext = cache.get(vNodeContext1) { mock() }

        assertThat(cache.toBeClosed).isEmpty()
        assertThat(retrievedContext).isSameAs(sandboxContext1)
    }

    @Test
    fun `when key in cache equal, don't replace`() {
        val cache = SandboxGroupContextCacheImpl(10)
        val vnodeContext1 = VirtualNodeContext(
            createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group"),
            setOf(SecureHash.parse("DUMMY:1234567890abcdef")),
            SandboxGroupType.FLOW,
            "filter")
        val equalVnodeContext1 = VirtualNodeContext(
            createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group"),
            setOf(SecureHash.parse("DUMMY:1234567890abcdef")),
            SandboxGroupType.FLOW,
            "filter")

        val sandboxContext1 = mock<CloseableSandboxGroupContext>()
        cache.get(vnodeContext1) { sandboxContext1 }
        val retrievedContext = cache.get(equalVnodeContext1) { mock() }

        assertThat(cache.toBeClosed).isEmpty()
        assertThat(retrievedContext).isSameAs(sandboxContext1)
    }

    @Test
    fun `sandboxes of different types do not trigger close on eviction of other sandbox types`() {
        val cache = SandboxGroupContextCacheImpl(2)

        val vncFlow = VirtualNodeContext(
            createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group"),
            setOf(SecureHash.parse("DUMMY:1234567890abcdef")),
            SandboxGroupType.FLOW,
            "filter")
        val vncPersistence = VirtualNodeContext(
            createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group"),
            setOf(SecureHash.parse("DUMMY:1234567890abcdef")),
            SandboxGroupType.PERSISTENCE,
            "filter")
        val vncVerification = VirtualNodeContext(
            createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group"),
            setOf(SecureHash.parse("DUMMY:1234567890abcdef")),
            SandboxGroupType.VERIFICATION,
            "filter")
        val sandboxContextFlow = mock<CloseableSandboxGroupContext>()
        val sandboxContextPersistence = mock<CloseableSandboxGroupContext>()
        val sandboxContextVerification = mock<CloseableSandboxGroupContext>()

        cache.get(vncFlow) { sandboxContextFlow }
        cache.get(vncPersistence) { sandboxContextPersistence }
        cache.get(vncVerification) { sandboxContextVerification }
        cache.get(vncFlow) { mock() }

        verify(sandboxContextFlow, never()).close()
        verify(sandboxContextPersistence, never()).close()
        verify(sandboxContextVerification, never()).close()
    }
}
