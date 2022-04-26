package net.corda.sandboxgroupcontext.impl

import net.corda.libs.packaging.CpkIdentifier
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCacheImpl
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
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
            on { x500Name } doReturn "name"
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
            on { x500Name } doReturn "name"
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
            on { x500Name } doReturn "name"
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
            on { x500Name } doReturn "name"
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
            HoldingIdentity("Alice", "group"),
            setOf(CpkIdentifier("cpk1", "1.0", SecureHash.create("DUMMY:1234567890abcdef"))),
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            "filter")

        val equalVnodeContext1 = VirtualNodeContext(
            HoldingIdentity("Alice", "group"),
            setOf(CpkIdentifier("cpk1", "1.0", SecureHash.create("DUMMY:1234567890abcdef"))),
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            "filter")

        val sandboxContext1 = mock<CloseableSandboxGroupContext>()

        cache.get(vnodeContext1) { sandboxContext1 }
        val retrievedContext = cache.get(equalVnodeContext1) { mock() }

        assertThat(retrievedContext).isSameAs(sandboxContext1)
    }
}