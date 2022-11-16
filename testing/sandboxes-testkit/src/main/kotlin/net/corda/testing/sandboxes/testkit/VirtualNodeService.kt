package net.corda.testing.sandboxes.testkit

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.virtualnode.VirtualNodeInfo

interface VirtualNodeService {
    fun loadVirtualNode(resourceName: String): VirtualNodeInfo
    fun unloadSandbox(sandboxGroupContext: SandboxGroupContext)
}
