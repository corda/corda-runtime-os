package net.corda.sandboxes.stresstest

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import org.junit.jupiter.api.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SandboxStressTestSample {

    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    @Test
    fun `this test runs`() {
        println("This test runs")
    }
}