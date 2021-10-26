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
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun sandboxGroupDoesNotReceiveBundleEventsFromOtherSandboxGroups() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all bundle events visible to this bundle.
        val bundleEvents = sandboxLoader.runFlow<List<BundleEvent>>(BUNDLE_EVENTS_FLOW, thisGroup)

        assertThat(bundleEvents).isNotEmpty
        bundleEvents.forEach { event ->
            assertTrue { !sandboxLoader.containsBundle(event.bundle, otherGroup) }
            assertTrue { !sandboxLoader.containsBundle(event.origin, otherGroup) }
        }
    }
}
