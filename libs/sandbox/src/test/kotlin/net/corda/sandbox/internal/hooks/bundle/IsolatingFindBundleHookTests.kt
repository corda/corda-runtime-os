package net.corda.sandbox.internal.hooks.bundle

import net.corda.sandbox.internal.SandboxInternal
import net.corda.sandbox.internal.SandboxServiceInternal
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.createMockBundleContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

class IsolatingFindBundleHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleOneContext = createMockBundleContext(bundleOne)
    private val bundleTwo = mock<Bundle>()
    private val candidates = mutableListOf(bundleTwo)

    @BeforeEach
    fun setup() {
        candidates.clear()
        candidates.add(bundleTwo)
    }

    @Test
    fun `bundle is found if a bundle has visibility`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
        }

        val isolatingFindBundleHook = IsolatingFindBundleHook(sandboxService)
        isolatingFindBundleHook.find(bundleOneContext, candidates)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `bundle is not found if a bundle does not have visibility`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
        }

        val isolatingFindBundleHook = IsolatingFindBundleHook(sandboxService)
        isolatingFindBundleHook.find(bundleOneContext, candidates)
        assertEquals(0, candidates.size)
    }

    @Test
    fun `bundle is found if a bundle is in a platform sandbox`() {
        val platformSandbox = mock<SandboxInternal>()

        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
            whenever(getSandbox(bundleOne)).thenReturn(platformSandbox)
            whenever(isPlatformSandbox(platformSandbox)).thenReturn(true)
        }

        val isolatingFindBundleHook = IsolatingFindBundleHook(sandboxService)
        isolatingFindBundleHook.find(bundleOneContext, candidates)
        assertEquals(1, candidates.size)
    }
}