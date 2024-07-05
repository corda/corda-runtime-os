package net.corda.ledger.lib.impl.stub.sandbox

import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext

class StubCurrentSandboxGroupContext : CurrentSandboxGroupContext {

    override fun get(): SandboxGroupContext {
        return StubSandboxGroupContext()
    }

    override fun set(sandboxGroupContext: SandboxGroupContext) {
        TODO("Not yet implemented")
    }

    override fun remove() {
        TODO("Not yet implemented")
    }
}
