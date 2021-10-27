package net.corda.sandboxhooks

import net.corda.sandbox.Sandbox
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

        /**
         * Indicates whether at least one of the provided bundles has the given [symbolicName] and is contained in the
         * [sandbox], and that there is no bundle out of the provided bundles that has the given [symbolicName] but
         * that is not contained in the [sandbox].
         */
        fun bundlesWithGivenNameAreFromSandbox(bundles: List<Bundle>, symbolicName: String, sandbox: Sandbox): Boolean {
            return bundles.any { bundle ->
                bundle.symbolicName == symbolicName && sandboxLoader.containsBundle(sandbox, bundle)
            } && bundles.none { bundle ->
                bundle.symbolicName == symbolicName && !sandboxLoader.containsBundle(sandbox, bundle)
            }
        }
    }

    @Test
    fun `sandbox can see its own bundles and main bundles in the same sandbox group only`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all bundle events visible to this bundle.
        val bundles = sandboxLoader.runFlow<List<Bundle>>(BUNDLES_FLOW, thisGroup)

        assertTrue(bundles.any { bundle -> sandboxLoader.containsBundle(thisGroup, bundle) })
        assertTrue(bundles.none { bundle -> sandboxLoader.containsBundle(otherGroup, bundle) })

        assertTrue(bundlesWithGivenNameAreFromSandbox(bundles, sandboxLoader.cpk1.metadata.id.name, sandboxLoader.sandbox1))
        assertTrue(bundlesWithGivenNameAreFromSandbox(bundles, sandboxLoader.cpk2.metadata.id.name, sandboxLoader.sandbox2))
        assertTrue(bundlesWithGivenNameAreFromSandbox(bundles, LIBRARY_BUNDLE_SYMBOLIC_NAME, sandboxLoader.sandbox1))
    }
}
