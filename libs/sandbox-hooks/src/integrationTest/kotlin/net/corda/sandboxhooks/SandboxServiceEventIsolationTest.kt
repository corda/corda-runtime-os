package net.corda.sandboxhooks

import net.corda.sandbox.SandboxGroup
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.ObjectAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.ServiceEvent
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class SandboxServiceEventIsolationTest {
    companion object {
        const val SERVICE_EVENT1_FLOW_CLASS = "com.example.sandbox.cpk1.ServiceEventOneFlow"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        fun assertThat(events: List<ServiceEvent>) = ServiceEventListAssertions(events)

        class ServiceEventListAssertions(events: List<ServiceEvent>)
            : AbstractListAssert<ServiceEventListAssertions, List<ServiceEvent>, ServiceEvent, ObjectAssert<ServiceEvent>>(events, ServiceEventListAssertions::class.java) {
            override fun toAssert(value: ServiceEvent?, description: String?) = ObjectAssert(value!!) // Never called.

            override fun newAbstractIterableAssert(iterable: Iterable<ServiceEvent>): ServiceEventListAssertions {
                return ServiceEventListAssertions(iterable as List<ServiceEvent>)
            }

            fun noneForSandboxGroup(group: SandboxGroup): ServiceEventListAssertions {
                return noneMatch { event ->
                    sandboxLoader.containsBundle(event.serviceReference.bundle, group)
                }
            }
        }
    }

    @Test
    fun sandboxGroupDoesNotReceiveServiceEventsFromOtherSandboxGroups() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val serviceEvents = sandboxLoader.runFlow<List<ServiceEvent>>(SERVICE_EVENT1_FLOW_CLASS, thisGroup)
        assertThat(serviceEvents)
            .noneForSandboxGroup(otherGroup)
            .isNotEmpty
    }
}
