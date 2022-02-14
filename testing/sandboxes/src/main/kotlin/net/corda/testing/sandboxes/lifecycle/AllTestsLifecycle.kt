package net.corda.testing.sandboxes.lifecycle

import java.util.function.Consumer
import net.corda.testing.sandboxes.SandboxSetup
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class AllTestsLifecycle : TestLifecycle, AfterAllCallback {
    private var sandboxSetup: SandboxSetup? = null

    override fun accept(setup: SandboxSetup, initializer: Consumer<SandboxSetup>) {
        sandboxSetup = setup
        setup.start()
        initializer.accept(setup)
    }

    override fun afterAll(context: ExtensionContext?) {
        sandboxSetup?.shutdown()
    }
}
