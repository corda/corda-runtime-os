package net.corda.sandboxhooks.service

import net.corda.sandbox.SandboxContextService
import net.corda.sandboxhooks.mockBundleContext
import net.corda.sandboxhooks.mockServiceReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

class IsolatingFindServiceHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleOneContext = mockBundleContext(bundleOne)
    private val bundleTwo = mock<Bundle>()
    private val bundleTwoServiceReference = mockServiceReference(bundleTwo)
    private val serviceReferences = mutableListOf(bundleTwoServiceReference)

    @BeforeEach
    fun setUp() {
        // We reset the list of service references to be filtered by the hook.
        serviceReferences.clear()
        serviceReferences.add(bundleTwoServiceReference)
    }

    @Test
    fun `service is found if a bundle has visibility`() {
        val sandboxService = mock<SandboxContextService>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
        }

        val isolatingFindServiceHook = IsolatingFindServiceHook(sandboxService)
        isolatingFindServiceHook.find(bundleOneContext, "", "", true, serviceReferences)
        assertEquals(1, serviceReferences.size)
    }

    @Test
    fun `service is not found if a bundle does not have visibility`() {
        val sandboxService = mock<SandboxContextService>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
        }

        val isolatingFindServiceHook = IsolatingFindServiceHook(sandboxService)
        isolatingFindServiceHook.find(bundleOneContext, "", "", true, serviceReferences)
        assertEquals(0, serviceReferences.size)
    }
}