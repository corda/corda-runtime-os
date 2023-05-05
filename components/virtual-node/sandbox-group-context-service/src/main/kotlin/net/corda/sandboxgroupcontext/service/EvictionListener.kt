package net.corda.sandboxgroupcontext.service

import net.corda.sandboxgroupcontext.VirtualNodeContext

fun interface EvictionListener {
    /**
     * Invoked when the [VirtualNodeContext] is evicted from
     * [SandboxGroupContextCache][net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCache].
     * Its sandbox is still active at this point, and will be closed when no longer referenced.
     *
     * We don't pass a reference to the [SandboxGroupContext][net.corda.sandboxgroupcontext.SandboxGroupContext]
     * itself here simply because we don't need to yet. And also because we are waiting for people to _stop_ using it.
     */
    fun onEviction(vnc: VirtualNodeContext)
}
