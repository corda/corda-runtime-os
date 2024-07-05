package net.corda.ledger.lib.impl.stub.sandbox

import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import java.util.concurrent.CompletableFuture

class StubSandboxGroupContext : SandboxGroupContext {

    override val sandboxGroup: SandboxGroup
        get() = StubSandboxGroup()


    override fun <T : Any> get(key: String, valueType: Class<out T>): T? {
        TODO("Not yet implemented")
    }

    override val completion: CompletableFuture<Boolean>
        get() = TODO("Not yet implemented")
    override val virtualNodeContext: VirtualNodeContext
        get() = TODO("Not yet implemented")
}
