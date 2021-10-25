package net.corda.sandboxhooks

import net.corda.sandbox.Sandbox
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class SandboxIsolationTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        private fun assertMutuallyVisible(sandboxA: Sandbox, sandboxB: Sandbox) {
            assertNotEquals(sandboxA, sandboxB)
            assertThat(hasVisibility(sandboxA, sandboxB)).isTrue
            assertThat(hasVisibility(sandboxB, sandboxA)).isTrue
        }

        private fun assertMutuallyInvisible(sandboxA: Sandbox, sandboxB: Sandbox) {
            assertThat(hasVisibility(sandboxA, sandboxB)).isFalse
            assertThat(hasVisibility(sandboxB, sandboxA)).isFalse
        }

        private fun hasVisibility(sandbox1: Sandbox, sandbox2: Sandbox): Boolean {
            val hasVisibilityMethod = sandbox1::class.java.getMethod("hasVisibility", Sandbox::class.java)
            return hasVisibilityMethod.invoke(sandbox1, sandbox2) as Boolean
        }
    }

    @Test
    fun twoSandboxesInTheSameGroupAreMutuallyVisible() {
        val sandbox1 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox2 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk2.metadata.id)

        assertMutuallyVisible(sandbox1, sandbox2)
    }

    @Test
    fun twoSandboxesInDifferentGroupsAreMutuallyInvisible() {
        val sandbox1 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox3 = sandboxLoader.group2.getSandbox(sandboxLoader.cpk3.metadata.id)

        assertMutuallyInvisible(sandbox1, sandbox3)
    }
}
