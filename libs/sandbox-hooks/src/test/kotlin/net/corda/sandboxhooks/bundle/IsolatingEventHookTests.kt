package net.corda.sandboxhooks.bundle

import net.corda.sandbox.SandboxContextService
import net.corda.sandboxhooks.mockBundleContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent

class IsolatingEventHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleOneContext = mockBundleContext(bundleOne)
    private val bundleTwo = mock<Bundle>()
    private val bundleTwoEvent = BundleEvent(0, bundleTwo)
    private val candidates = mutableListOf(bundleOneContext)

    @BeforeEach
    fun setup() {
        candidates.clear()
        candidates.add(bundleOneContext)
    }

    @Test
    fun `event is received if a bundle has visibility`() {
        val sandboxService = mock<SandboxContextService>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
        }

        val isolatingEventHook = IsolatingEventHook(sandboxService)
        isolatingEventHook.event(bundleTwoEvent, candidates)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `event is not received if a bundle does not have visibility`() {
        val sandboxService = mock<SandboxContextService>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
        }

        val isolatingEventHook = IsolatingEventHook(sandboxService)
        isolatingEventHook.event(bundleTwoEvent, candidates)
        assertEquals(0, candidates.size)
    }
}