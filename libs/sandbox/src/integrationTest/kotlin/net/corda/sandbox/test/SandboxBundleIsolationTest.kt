package net.corda.sandbox.test

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.test.SandboxLoader.Companion.LIBRARY_SYMBOLIC_NAME
import net.corda.v5.application.flows.Flow
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.ObjectAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.framework.Bundle
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class SandboxBundleIsolationTest {
    companion object {
        const val BUNDLES1_FLOW_CLASS = "com.example.sandbox.cpk1.BundlesOneFlow"
        const val BUNDLES2_FLOW_CLASS = "com.example.sandbox.cpk2.BundlesTwoFlow"
        const val BUNDLES3_FLOW_CLASS = "com.example.sandbox.cpk3.BundlesThreeFlow"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        private fun runFlow(className: String, group: SandboxGroup): List<Bundle> {
            val workflowClass = group.loadClass(className, Flow::class.java)
            @Suppress("unchecked_cast")
            return sandboxLoader.getServiceFor(Flow::class.java, workflowClass).call() as? List<Bundle>
                ?: fail("Workflow does not return a List")
        }

        fun assertThat(bundles: List<Bundle>) = BundleListAssertions(bundles)

        class BundleListAssertions(bundles: List<Bundle>)
            : AbstractListAssert<BundleListAssertions, List<Bundle>, Bundle, ObjectAssert<Bundle>>(bundles, BundleListAssertions::class.java) {
            override fun toAssert(value: Bundle?, description: String?): ObjectAssert<Bundle> {
                TODO("Not yet implemented")
            }

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
    fun testBundlesForCPK1() {
        val thisGroup = sandboxLoader.group1
        val sandbox1 = thisGroup.getSandbox(sandboxLoader.cpk1.id)
        val sandbox2 = thisGroup.getSandbox(sandboxLoader.cpk2.id)
        val bundles = runFlow(BUNDLES1_FLOW_CLASS, thisGroup).onEach(::println)

        // CPK1 should be able to see its own bundles, and
        // CPK2's "main" jar bundle, but nothing from CPK3.
        assertThat(bundles)
            .anyMatch { sandboxLoader.containsBundle(it, thisGroup) }
            .noneMatch { sandboxLoader.containsBundle(it, sandboxLoader.group2) }

            // CPK1 can see both its own and CPK2's "main" bundles.
            .existsOnlyInsideSandbox(sandboxLoader.cpk1.id.symbolicName, sandbox1)
            .existsOnlyInsideSandbox(sandboxLoader.cpk2.id.symbolicName, sandbox2)

            // CPK1 can only see its own library bundle.
            .existsOnlyInsideSandbox(LIBRARY_SYMBOLIC_NAME, sandbox1)
    }

    @Test
    fun testBundlesForCPK2() {
        val thisGroup = sandboxLoader.group1
        val sandbox1 = thisGroup.getSandbox(sandboxLoader.cpk1.id)
        val sandbox2 = thisGroup.getSandbox(sandboxLoader.cpk2.id)
        val bundles = runFlow(BUNDLES2_FLOW_CLASS, thisGroup).onEach(::println)

        // CPK2 should be able to see its own bundles, and
        // CPK1's "main" jar bundle, but nothing from CPK3.
        assertThat(bundles)
            .anyMatch { sandboxLoader.containsBundle(it, thisGroup) }
            .noneMatch { sandboxLoader.containsBundle(it, sandboxLoader.group2) }

            // CPK2 can see both its own and CPK1's "main" bundles.
            .existsOnlyInsideSandbox(sandboxLoader.cpk1.id.symbolicName, sandbox1)
            .existsOnlyInsideSandbox(sandboxLoader.cpk2.id.symbolicName, sandbox2)

            // CPK2 can only see its own library bundle.
            .existsOnlyInsideSandbox(LIBRARY_SYMBOLIC_NAME, sandbox2)
    }

    @Test
    fun testBundlesForCPK3() {
        val thisGroup = sandboxLoader.group2
        val sandbox3 = thisGroup.getSandbox(sandboxLoader.cpk3.id)
        val bundles = runFlow(BUNDLES3_FLOW_CLASS, thisGroup).onEach(::println)

        // CPK3 should be able to see its own bundles,
        // but nothing from either CPK1 or CPK2.
        assertThat(bundles)
            .anyMatch { sandboxLoader.containsBundle(it, thisGroup) }
            .noneMatch { sandboxLoader.containsBundle(it, sandboxLoader.group1) }

            // CPK3 can see its own "main" bundle.
            .existsOnlyInsideSandbox(sandboxLoader.cpk3.id.symbolicName, sandbox3)

            // CPK3 can only see its own library bundle
            .existsOnlyInsideSandbox(LIBRARY_SYMBOLIC_NAME, sandbox3)
    }
}
