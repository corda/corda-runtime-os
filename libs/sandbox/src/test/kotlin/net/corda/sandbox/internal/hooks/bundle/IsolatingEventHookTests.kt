package net.corda.sandbox.internal.hooks.bundle

import net.corda.sandbox.internal.SandboxServiceInternal
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.createMockBundleContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent

class IsolatingEventHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleOneContext = createMockBundleContext(bundleOne)
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
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
            whenever(isStarted).thenReturn(true)
        }

        val isolatingEventHook = IsolatingEventHook(sandboxService)
        isolatingEventHook.event(bundleTwoEvent, candidates)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `event is not received if a bundle does not have visibility`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
            whenever(isStarted).thenReturn(true)
        }

        val isolatingEventHook = IsolatingEventHook(sandboxService)
        isolatingEventHook.event(bundleTwoEvent, candidates)
        assertEquals(0, candidates.size)
    }
}