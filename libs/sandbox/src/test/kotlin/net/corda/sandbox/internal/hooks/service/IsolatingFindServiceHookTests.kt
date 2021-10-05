package net.corda.sandbox.internal.hooks.service

import net.corda.sandbox.internal.SandboxServiceInternal
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.createMockBundleContext
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.createMockServiceReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

class IsolatingFindServiceHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleOneContext = createMockBundleContext(bundleOne)
    private val bundleTwo = mock<Bundle>()
    private val bundleTwoServiceReference = createMockServiceReference(bundleTwo)
    private val serviceReferences = mutableListOf(bundleTwoServiceReference)

    @BeforeEach
    fun setUp() {
        // We reset the list of service references to be filtered by the hook.
        serviceReferences.clear()
        serviceReferences.add(bundleTwoServiceReference)
    }

    @Test
    fun `service is found if a bundle has visibility`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
            whenever(isStarted).thenReturn(true)
        }

        val isolatingFindServiceHook = IsolatingFindServiceHook(sandboxService)
        isolatingFindServiceHook.find(bundleOneContext, "", "", true, serviceReferences)
        assertEquals(1, serviceReferences.size)
    }

    @Test
    fun `service is not found if a bundle does not have visibility`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
            whenever(isStarted).thenReturn(true)
        }

        val isolatingFindServiceHook = IsolatingFindServiceHook(sandboxService)
        isolatingFindServiceHook.find(bundleOneContext, "", "", true, serviceReferences)
        assertEquals(0, serviceReferences.size)
    }
}