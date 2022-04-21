package net.corda.sandboxgroupcontext.impl

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCache
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SandboxGroupContextCacheTest {

    @Test
    fun `when cache full, evict and close evicted`() {
        val cache = SandboxGroupContextCache(1)

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


        verify(sandboxContext1).close()
    }

    @Test
    fun `when cache closed, close everything`() {
        val cache = SandboxGroupContextCache()

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

        verify(sandboxContext1).close()
        verify(sandboxContext2).close()
    }

    @Test
    fun `when remove also close`() {
        val cache = SandboxGroupContextCache()

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

        verify(sandboxContext1).close()
    }
}