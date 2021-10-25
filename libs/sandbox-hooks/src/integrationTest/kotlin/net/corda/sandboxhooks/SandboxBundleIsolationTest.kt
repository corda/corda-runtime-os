package net.corda.sandboxhooks

import net.corda.sandbox.Sandbox
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.ObjectAssert
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

        fun assertThat(bundles: List<Bundle>) = BundleListAssertions(bundles)

        class BundleListAssertions(bundles: List<Bundle>)
            : AbstractListAssert<BundleListAssertions, List<Bundle>, Bundle, ObjectAssert<Bundle>>(bundles, BundleListAssertions::class.java) {
            override fun toAssert(value: Bundle?, description: String?) = ObjectAssert(value!!) // Never called.

            override fun newAbstractIterableAssert(iterable: Iterable<Bundle>): BundleListAssertions {
                return BundleListAssertions(iterable as List<Bundle>)
            }

            fun existsOnlyInsideSandbox(symbolicName: String, sandbox: Sandbox): BundleListAssertions {
                return anyMatch { bundle ->
                    // A bundle with this symbolic name exists in target sandbox.
                    bundle.symbolicName == symbolicName && sandboxLoader.containsBundle(bundle, sandbox)
                }.noneMatch { bundle ->
                    // No bundle with this symbolic name exists in a different sandbox.
                    bundle.symbolicName == symbolicName && !sandboxLoader.containsBundle(bundle, sandbox)
                }
            }
        }
    }

    @Test
    fun sandboxCanSeeItsOwnBundlesAndMainBundlesInTheSameSandboxGroupOnly() {
        val thisGroup = sandboxLoader.group1
        val sandbox1 = thisGroup.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox2 = thisGroup.getSandbox(sandboxLoader.cpk2.metadata.id)

        // This flow returns all bundle events visible to this bundle.
        val bundles = sandboxLoader.runFlow<List<Bundle>>(BUNDLES1_FLOW_CLASS, thisGroup)

        // CPK1 should be able to see its own bundles, and CPK2's "main" jar bundle, but nothing from CPK3.
        assertThat(bundles)
            .anyMatch { sandboxLoader.containsBundle(it, thisGroup) }
            .noneMatch { sandboxLoader.containsBundle(it, sandboxLoader.group2) }

            // CPK1 can see both its own and CPK2's "main" bundles.
            .existsOnlyInsideSandbox(sandboxLoader.cpk1.metadata.id.name, sandbox1)
            .existsOnlyInsideSandbox(sandboxLoader.cpk2.metadata.id.name, sandbox2)

            // Only CPK1 can see its own library bundle.
            .existsOnlyInsideSandbox(LIBRARY_BUNDLE_SYMBOLIC_NAME, sandbox1)
    }
}
