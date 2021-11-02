package net.corda.sandboxtests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.ServiceEvent
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of service events across sandbox groups. */
@ExtendWith(ServiceExtension::class)
class SandboxServiceEventIsolationTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `sandbox receives service events from its own sandbox group`() {
        val thisGroup = sandboxLoader.group1

        // This flow returns all service events visible to this bundle.
        val serviceEvents = runFlow<List<ServiceEvent>>(thisGroup, SERVICE_EVENTS_FLOW)

        assertTrue(serviceEvents.any { event -> sandboxGroupContainsBundle(thisGroup, event.serviceReference.bundle) })
    }

    @Test
    fun `sandbox does not receive service events from other sandbox groups`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2

        // This flow returns all service events visible to this bundle.
        val serviceEvents = runFlow<List<ServiceEvent>>(thisGroup, SERVICE_EVENTS_FLOW)

        assertTrue(serviceEvents.none { event -> sandboxGroupContainsBundle(otherGroup, event.serviceReference.bundle) })
    }
}
