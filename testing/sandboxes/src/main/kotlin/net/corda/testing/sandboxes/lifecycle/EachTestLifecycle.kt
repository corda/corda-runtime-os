package net.corda.testing.sandboxes.lifecycle

import net.corda.testing.sandboxes.SandboxSetup
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.function.Consumer

class EachTestLifecycle : TestLifecycle, BeforeEachCallback, AfterEachCallback {
    private var initializer: Consumer<SandboxSetup> = Consumer {}
    private var sandboxSetup: SandboxSetup? = null

    override fun accept(setup: SandboxSetup, initializer: Consumer<SandboxSetup>) {
        this.initializer = initializer
        sandboxSetup = setup
    }

    override fun beforeEach(context: ExtensionContext?) {
        sandboxSetup?.also { setup ->
            setup.start()
            initializer.accept(setup)
        } ?: throw IllegalStateException("Lifecycle not configured.")
    }

    override fun afterEach(context: ExtensionContext?) {
        sandboxSetup?.shutdown()
    }
}
