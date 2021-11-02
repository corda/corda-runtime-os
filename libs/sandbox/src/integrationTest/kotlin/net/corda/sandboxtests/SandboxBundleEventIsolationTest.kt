package net.corda.sandboxtests

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
    fun `sandbox receives bundle events from its own sandbox group`() {
        val thisGroup = sandboxLoader.group1

        // This flow returns all bundle events visible to this bundle.
        val bundleEvents = runFlow<List<BundleEvent>>(thisGroup, BUNDLE_EVENTS_FLOW)

        assertTrue(bundleEvents.any { event -> sandboxGroupContainsBundle(thisGroup, event.bundle) })
        assertTrue(bundleEvents.any { event -> sandboxGroupContainsBundle(thisGroup, event.origin) })
    }

    @Test
    fun `sandbox does not receive bundle events from other sandbox groups`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all bundle events visible to this bundle.
        val bundleEvents = runFlow<List<BundleEvent>>(thisGroup, BUNDLE_EVENTS_FLOW)

        assertTrue(bundleEvents.none { event -> sandboxGroupContainsBundle(otherGroup, event.bundle) })
        assertTrue(bundleEvents.none { event -> sandboxGroupContainsBundle(otherGroup, event.origin) })
    }
}
