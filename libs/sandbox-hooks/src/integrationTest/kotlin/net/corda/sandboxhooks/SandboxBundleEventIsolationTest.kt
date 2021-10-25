package net.corda.sandboxhooks

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.BundleEvent
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of bundle events across sandbox groups. */
@ExtendWith(ServiceExtension::class)
class SandboxBundleEventIsolationTest {
    companion object {
        const val BUNDLE_EVENT1_FLOW_CLASS = "com.example.sandbox.cpk1.BundleEventOneFlow"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun sandboxGroupDoesNotReceiveBundleEventsFromOtherSandboxGroups() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all bundle events visible to this bundle.
        val bundleEvents = sandboxLoader.runFlow<List<BundleEvent>>(BUNDLE_EVENT1_FLOW_CLASS, thisGroup)

        assertThat(bundleEvents).isNotEmpty
        bundleEvents.forEach { evt ->
            assertTrue { !sandboxLoader.containsBundle(evt.bundle, otherGroup) }
            assertTrue { !sandboxLoader.containsBundle(evt.origin, otherGroup) }
        }
    }
}
