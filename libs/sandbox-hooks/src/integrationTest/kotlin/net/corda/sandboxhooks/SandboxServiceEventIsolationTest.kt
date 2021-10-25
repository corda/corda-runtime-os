package net.corda.sandboxhooks

import org.assertj.core.api.Assertions.assertThat
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
        const val SERVICE_EVENT1_FLOW_CLASS = "com.example.sandbox.cpk1.ServiceEventOneFlow"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun sandboxGroupDoesNotReceiveServiceEventsFromOtherSandboxGroups() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val serviceEvents = sandboxLoader.runFlow<List<ServiceEvent>>(SERVICE_EVENT1_FLOW_CLASS, thisGroup)
        assertThat(serviceEvents).isNotEmpty
        serviceEvents.forEach { event ->
            assertTrue { !sandboxLoader.containsBundle(event.serviceReference.bundle, otherGroup) }
        }
    }
}
