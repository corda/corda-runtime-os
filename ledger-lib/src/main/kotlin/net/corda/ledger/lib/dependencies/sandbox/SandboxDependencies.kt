package net.corda.ledger.lib.dependencies.sandbox

import net.corda.ledger.lib.impl.stub.sandbox.StubCurrentSandboxGroupContext

object SandboxDependencies {
    val currentSandboxGroupContext = StubCurrentSandboxGroupContext()
}