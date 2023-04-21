package net.corda.sandboxhooks.bundle

import net.corda.sandbox.SandboxContextService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

class IsolatingCollisionBundleHookTests {
    private val target = mock<Bundle>()
    private val bundle = mock<Bundle>()
    private val candidates = mutableListOf(bundle)

    @BeforeEach
    fun setup() {
        candidates.clear()
        candidates.add(bundle)
    }

    @Test
    fun `sandboxed candidate does not represent a collision`() {
        val sandboxService = mock<SandboxContextService>().apply {
            whenever(isSandboxed(bundle)).thenReturn(true)
        }

        val isolatingCollisionBundleHook = IsolatingCollisionBundleHook(sandboxService)
        isolatingCollisionBundleHook.filterCollisions(0, target, candidates)
        assertEquals(0, candidates.size)
    }

    @Test
    fun `non-sandboxed candidate represents a collision`() {
        val sandboxService = mock<SandboxContextService>()

        val isolatingCollisionBundleHook = IsolatingCollisionBundleHook(sandboxService)
        isolatingCollisionBundleHook.filterCollisions(0, target, candidates)
        assertEquals(1, candidates.size)
    }
}