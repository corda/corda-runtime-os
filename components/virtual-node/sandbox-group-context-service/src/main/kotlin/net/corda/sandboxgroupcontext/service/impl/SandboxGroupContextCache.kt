package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext

interface SandboxGroupContextCache : AutoCloseable {
    val cacheSize: Long
    fun remove(virtualNodeContext: VirtualNodeContext)
    fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext
}

