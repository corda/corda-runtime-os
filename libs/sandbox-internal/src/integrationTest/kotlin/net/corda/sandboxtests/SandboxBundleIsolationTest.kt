package net.corda.sandboxtests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.Bundle
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of bundles across sandbox groups. */
@ExtendWith(ServiceExtension::class)
class SandboxBundleIsolationTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `sandbox can see bundles in its own sandbox group`() {
        val thisGroup = sandboxLoader.group1
        // This flow returns all bundles visible to this bundle.
        val bundles = runFlow<List<Bundle>>(thisGroup, BUNDLES_FLOW)

        val expectedBundleNames = setOf(
            FRAMEWORK_BUNDLE_NAME, SCR_BUNDLE_NAME, CPK_ONE_BUNDLE_NAME, CPK_TWO_BUNDLE_NAME, CPK_LIBRARY_BUNDLE_NAME
        )

        assertTrue(bundles.any { bundle -> sandboxGroupContainsBundle(thisGroup, bundle) })
        assertTrue(bundles.map(Bundle::getSymbolicName).containsAll(expectedBundleNames))
    }

    @Test
    fun `sandbox cannot see bundles in other sandbox groups`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all bundles visible to this bundle.
        val bundles = runFlow<List<Bundle>>(thisGroup, BUNDLES_FLOW)

        assertTrue(bundles.none { bundle -> sandboxGroupContainsBundle(otherGroup, bundle) })
    }
}
