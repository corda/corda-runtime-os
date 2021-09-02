package net.corda.sandbox.test

import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.ObjectAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.framework.BundleEvent
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class SandboxBundleEventIsolationTest {
    companion object {
        const val BUNDLE_EVENT1_FLOW_CLASS = "com.example.sandbox.cpk1.BundleEventOneFlow"
        const val BUNDLE_EVENT2_FLOW_CLASS = "com.example.sandbox.cpk2.BundleEventTwoFlow"
        const val BUNDLE_EVENT3_FLOW_CLASS = "com.example.sandbox.cpk3.BundleEventThreeFlow"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        private fun runFlow(className: String, group: SandboxGroup): List<BundleEvent> {
            val workflowClass = group.loadClass(className, Flow::class.java)
            @Suppress("unchecked_cast")
            return sandboxLoader.getServiceFor(Flow::class.java, workflowClass).call() as? List<BundleEvent>
                ?: fail("Workflow does not return a List")
        }

        fun assertThat(events: List<BundleEvent>) = BundleEventListAssertions(events)

        class BundleEventListAssertions(events: List<BundleEvent>)
            : AbstractListAssert<BundleEventListAssertions, List<BundleEvent>, BundleEvent, ObjectAssert<BundleEvent>>(events, BundleEventListAssertions::class.java) {
            override fun toAssert(value: BundleEvent?, description: String?) = ObjectAssert(value!!) // Never called.

            override fun newAbstractIterableAssert(iterable: Iterable<BundleEvent>): BundleEventListAssertions {
                return BundleEventListAssertions(iterable as List<BundleEvent>)
            }

            fun noneForSandboxGroup(group: SandboxGroup): BundleEventListAssertions {
                return noneMatch { evt -> sandboxLoader.containsBundle(evt.bundle, group) }
                    .noneMatch { evt -> sandboxLoader.containsBundle(evt.origin, group) }
            }
        }
    }

    @Test
    fun testBundleEventsForCPK1() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val bundleEvents = runFlow(BUNDLE_EVENT1_FLOW_CLASS, thisGroup).onEach(::println)
        assertThat(bundleEvents)
            .noneForSandboxGroup(otherGroup)
            .isNotEmpty
    }

    @Test
    fun testBundleEventsForCPK2() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val bundleEvents = runFlow(BUNDLE_EVENT2_FLOW_CLASS, thisGroup).onEach(::println)
        assertThat(bundleEvents)
            .noneForSandboxGroup(otherGroup)
            .isNotEmpty
    }

    @Test
    fun testBundleEventsForCPK3() {
        val thisGroup = sandboxLoader.group2
        val otherGroup = sandboxLoader.group1
        val bundleEvents = runFlow(BUNDLE_EVENT3_FLOW_CLASS, thisGroup).onEach(::println)
        assertThat(bundleEvents)
            .noneForSandboxGroup(otherGroup)
            .isNotEmpty
    }
}
