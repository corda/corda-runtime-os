package net.corda.sandbox.test

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
        const val SERVICE_EVENT2_FLOW_CLASS = "com.example.sandbox.cpk2.ServiceEventTwoFlow"
        const val SERVICE_EVENT3_FLOW_CLASS = "com.example.sandbox.cpk3.ServiceEventThreeFlow"

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
    fun testServiceEventsForCPK1() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val serviceEvents = sandboxLoader.runFlow<List<ServiceEvent>>(SERVICE_EVENT1_FLOW_CLASS, thisGroup).onEach(::println)
        assertThat(serviceEvents)
            .noneForSandboxGroup(otherGroup)
            .isNotEmpty
    }

    @Test
    fun testServiceEventsForCPK2() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val serviceEvents = sandboxLoader.runFlow<List<ServiceEvent>>(SERVICE_EVENT2_FLOW_CLASS, thisGroup).onEach(::println)
        assertThat(serviceEvents)
            .noneForSandboxGroup(otherGroup)
            .isNotEmpty
    }

    @Test
    fun testServiceEventsForCPK3() {
        val thisGroup = sandboxLoader.group2
        val otherGroup = sandboxLoader.group1
        val serviceEvents = sandboxLoader.runFlow<List<ServiceEvent>>(SERVICE_EVENT3_FLOW_CLASS, thisGroup).onEach(::println)
        assertThat(serviceEvents)
            .noneForSandboxGroup(otherGroup)
            .isNotEmpty
    }
}
