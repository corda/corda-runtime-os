package net.corda.sandboxhooks

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
    fun `sandbox can see its own bundles and main bundles in the same sandbox group only`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all bundles visible to this bundle.
        val bundles = sandboxLoader.runFlow<List<Bundle>>(BUNDLES_FLOW, thisGroup)

        assertTrue(bundles.any { bundle -> sandboxLoader.containsBundle(thisGroup, bundle) })
        assertTrue(bundles.none { bundle -> sandboxLoader.containsBundle(otherGroup, bundle) })
    }
}
