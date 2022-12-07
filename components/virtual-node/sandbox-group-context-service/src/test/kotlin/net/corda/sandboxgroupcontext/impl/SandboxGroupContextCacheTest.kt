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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class SandboxGroupContextCacheTest {

    @Test
    fun `when cache full, evict and close evicted`() {
        val cache = SandboxGroupContextCacheImpl(1)

        val id = mock<HoldingIdentity> {
            on { x500Name } doReturn MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB")
        }
        val vnodeContext1 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn id
        }

        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vnodeContext1) { sandboxContext1 }
        for (i in 1..100) {
            cache.get(mock {
                on { sandboxGroupType } doReturn SandboxGroupType.FLOW
                on { holdingIdentity } doReturn id
            }) { mock() }
        }


        verify(sandboxContext1, timeout(5000)).close()
    }

    @Test
    fun `when cache closed, close everything`() {
        val cache = SandboxGroupContextCacheImpl(10)

        val id = mock<HoldingIdentity> {
            on { x500Name } doReturn MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB")
        }
        val vnodeContext1 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn id
        }
        val vnodeContext2 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn id
        }

        val sandboxContext1 = mock<CloseableSandboxGroupContext>()
        val sandboxContext2 = mock<CloseableSandboxGroupContext>()

        cache.get(vnodeContext1) { sandboxContext1 }
        cache.get(vnodeContext2) { sandboxContext2 }

        cache.close()

        verify(sandboxContext1, timeout(5000)).close()
        verify(sandboxContext2, timeout(5000)).close()
    }

    @Test
    fun `when remove also close`() {
        val cache = SandboxGroupContextCacheImpl(10)

        val id = mock<HoldingIdentity> {
            on { x500Name } doReturn MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB")
        }
        val vnodeContext1 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn id
        }

        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vnodeContext1) { sandboxContext1 }
        cache.remove(vnodeContext1)

        verify(sandboxContext1, timeout(5000)).close()
    }

    @Test
    fun `when in cache retrieve same`() {
        val cache = SandboxGroupContextCacheImpl(10)

        val id = mock<HoldingIdentity> {
            on { x500Name } doReturn MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB")
        }
        val vnodeContext1 = mock<VirtualNodeContext> {
            on { sandboxGroupType } doReturn SandboxGroupType.FLOW
            on { holdingIdentity } doReturn id
        }

        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vnodeContext1) { sandboxContext1 }

        val retrievedContext = cache.get(vnodeContext1) { mock() }

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

        assertThat(retrievedContext).isSameAs(sandboxContext1)
    }

    @Test
    fun `sandboxes of different types do not trigger eviction of other sandbox types`() {
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
        val retrievedContext = cache.get(vncFlow) { mock() }
        assertThat(retrievedContext).isSameAs(sandboxContextFlow)
    }
}