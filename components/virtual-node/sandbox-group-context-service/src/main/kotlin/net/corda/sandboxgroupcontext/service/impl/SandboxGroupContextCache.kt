package net.corda.sandboxgroupcontext.service.impl

import java.util.concurrent.CompletableFuture
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextPreRemovalCallback
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction

interface SandboxGroupContextCache : CacheEviction, AutoCloseable {
    val capacities: Map<SandboxGroupType, Long>

    fun get(
        virtualNodeContext: VirtualNodeContext,
        preSandboxRemovalCallback: SandboxGroupContextPreRemovalCallback? = null,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext

    fun resize(sandboxGroupType: SandboxGroupType, newCapacity: Long)
    fun flush(): CompletableFuture<*>
}
