package net.corda.sandboxhooks

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
    fun `sandbox group receives bundle events from its own group, and not from other groups`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all bundle events visible to this bundle.
        val bundleEvents = sandboxLoader.runFlow<List<BundleEvent>>(BUNDLE_EVENTS_FLOW, thisGroup)

        assertTrue(bundleEvents.any { event -> sandboxLoader.containsBundle(thisGroup, event.bundle) })
        assertTrue(bundleEvents.any { event -> sandboxLoader.containsBundle(thisGroup, event.origin) })
        assertTrue(bundleEvents.none { event -> sandboxLoader.containsBundle(otherGroup, event.bundle) })
        assertTrue(bundleEvents.none { event -> sandboxLoader.containsBundle(otherGroup, event.origin) })
    }
}
