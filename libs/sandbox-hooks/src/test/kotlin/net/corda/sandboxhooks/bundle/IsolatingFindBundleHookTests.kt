package net.corda.sandboxhooks.bundle

import net.corda.sandbox.SandboxContextService
import net.corda.sandboxhooks.mockBundleContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

class IsolatingFindBundleHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleOneContext = mockBundleContext(bundleOne)
    private val bundleTwo = mock<Bundle>()
    private val candidates = mutableListOf(bundleTwo)

    @BeforeEach
    fun setup() {
        candidates.clear()
        candidates.add(bundleTwo)
    }

    @Test
    fun `bundle is not filtered out if looking bundle has visibility of it`() {
        val sandboxService = mock<SandboxContextService>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
        }

        val isolatingFindBundleHook = IsolatingFindBundleHook(sandboxService)
        isolatingFindBundleHook.find(bundleOneContext, candidates)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `bundle is filtered out if looking bundle does not have visibility of it`() {
        val sandboxService = mock<SandboxContextService>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
        }

        val isolatingFindBundleHook = IsolatingFindBundleHook(sandboxService)
        isolatingFindBundleHook.find(bundleOneContext, candidates)
        assertEquals(0, candidates.size)
    }
}