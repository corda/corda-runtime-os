package net.corda.sandboxhooks

import net.corda.sandbox.Sandbox
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of sandboxes across sandbox groups. */
@ExtendWith(ServiceExtension::class)
class SandboxIsolationTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        private fun hasVisibility(sandbox1: Sandbox, sandbox2: Sandbox): Boolean {
            val hasVisibilityMethod = sandbox1::class.java.getMethod("hasVisibility", Sandbox::class.java)
            return hasVisibilityMethod.invoke(sandbox1, sandbox2) as Boolean
        }
    }

    @Test
    fun twoSandboxesInTheSameGroupAreMutuallyVisible() {
        val sandbox1 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox2 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk2.metadata.id)

        assertTrue(hasVisibility(sandbox1, sandbox2))
        assertTrue(hasVisibility(sandbox2, sandbox1))
    }

    @Test
    fun twoSandboxesInDifferentGroupsAreMutuallyInvisible() {
        val sandbox1 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox3 = sandboxLoader.group2.getSandbox(sandboxLoader.cpk3.metadata.id)

        assertTrue(!hasVisibility(sandbox1, sandbox3))
        assertTrue(!hasVisibility(sandbox3, sandbox1))
    }
}
