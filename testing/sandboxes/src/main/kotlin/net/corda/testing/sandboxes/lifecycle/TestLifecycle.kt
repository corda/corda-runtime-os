package net.corda.testing.sandboxes.lifecycle

import java.util.function.Consumer
import net.corda.testing.sandboxes.SandboxSetup

interface TestLifecycle {
    fun accept(setup: SandboxSetup, initializer: Consumer<SandboxSetup>)
}
