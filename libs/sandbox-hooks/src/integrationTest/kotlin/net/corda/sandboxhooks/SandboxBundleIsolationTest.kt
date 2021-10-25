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
        const val BUNDLES1_FLOW_CLASS = "com.example.sandbox.cpk1.BundlesOneFlow"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        /**
         * At least one of the provided bundles has the given [symbolicName] and is contained in the [sandbox].
         *
         * There is no bundle out of the provided bundles that has the given [symbolicName] but that is not
         * contained in the [sandbox].
         */
        fun bundlesWithGivenNameAreFromSandbox(bundles: List<Bundle>, symbolicName: String, sandbox: Sandbox): Boolean {
            return bundles.any { bundle ->
                bundle.symbolicName == symbolicName && sandboxLoader.containsBundle(bundle, sandbox)
            } && bundles.none { bundle ->
                bundle.symbolicName == symbolicName && !sandboxLoader.containsBundle(bundle, sandbox)
            }
        }
    }

    @Test
    fun sandboxCanSeeItsOwnBundlesAndMainBundlesInTheSameSandboxGroupOnly() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val sandbox1 = thisGroup.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox2 = thisGroup.getSandbox(sandboxLoader.cpk2.metadata.id)

        // This flow returns all bundle events visible to this bundle.
        val bundles = sandboxLoader.runFlow<List<Bundle>>(BUNDLES1_FLOW_CLASS, thisGroup)

        assertTrue(bundles.any { bundle -> sandboxLoader.containsBundle(bundle, thisGroup) })
        assertTrue(bundles.none { bundle -> sandboxLoader.containsBundle(bundle, otherGroup) })

        assertTrue(bundlesWithGivenNameAreFromSandbox(bundles, sandboxLoader.cpk1.metadata.id.name, sandbox1))
        assertTrue(bundlesWithGivenNameAreFromSandbox(bundles, sandboxLoader.cpk2.metadata.id.name, sandbox2))
        assertTrue(bundlesWithGivenNameAreFromSandbox(bundles, LIBRARY_BUNDLE_SYMBOLIC_NAME, sandbox1))
    }
}
