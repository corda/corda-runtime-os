package net.corda.sandbox.test

import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.test.SandboxLoader.Companion.QUERY_CLASS
import net.corda.v5.application.flows.Flow
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.ObjectAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.service.resolver.Resolver
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class SandboxServiceIsolationTest {
    companion object {
        const val SERVICES1_FLOW_CLASS = "com.example.sandbox.cpk1.ServicesOneFlow"
        const val SERVICES2_FLOW_CLASS = "com.example.sandbox.cpk2.ServicesTwoFlow"
        const val SERVICES3_FLOW_CLASS = "com.example.sandbox.cpk3.ServicesThreeFlow"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        private fun runFlow(className: String, group: SandboxGroup): List<Class<out Any>> {
            val workflowClass = group.loadClass(className, Flow::class.java)
            @Suppress("unchecked_cast")
            return sandboxLoader.getServiceFor(Flow::class.java, workflowClass).call() as? List<Class<out Any>>
                ?: fail("Workflow does not return a List")
        }

        fun assertThat(classes: List<Class<out Any>>) = ServiceClassListAssertions(classes)

        class ServiceClassListAssertions(classes: List<Class<out Any>>)
            : AbstractListAssert<ServiceClassListAssertions, List<Class<out Any>>, Class<out Any>, ObjectAssert<Class<out Any>>>(classes, ServiceClassListAssertions::class.java) {
            override fun toAssert(value: Class<out Any>?, description: String?) = ObjectAssert(value!!) // Never called.

            override fun newAbstractIterableAssert(iterable: Iterable<Class<out Any>>): ServiceClassListAssertions {
                return ServiceClassListAssertions(iterable as List<Class<out Any>>)
            }

            fun hasNoServiceFromGroup(group: SandboxGroup): ServiceClassListAssertions {
                return noneMatch { svc -> sandboxLoader.containsClass(svc, group) }
            }

            fun hasNoService(serviceClass: Class<*>): ServiceClassListAssertions {
                return noneMatch { svc -> serviceClass.isAssignableFrom(svc) }
            }

            fun hasService(serviceClass: Class<*>): ServiceClassListAssertions {
                return anyMatch { svc -> serviceClass.isAssignableFrom(svc) }
            }
        }
    }

    @Test
    fun testServicesForCPK1() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val sandbox1 = thisGroup.getSandbox(sandboxLoader.cpk1.id)
        val sandbox2 = thisGroup.getSandbox(sandboxLoader.cpk2.id)
        val serviceClasses = runFlow(SERVICES1_FLOW_CLASS, thisGroup).onEach(::println)

        // CPK1 should be able to see its own services, and any
        // services inside CPK2's "main" jar, but nothing from CPK3.
        assertThat(serviceClasses)
            .hasNoServiceFromGroup(otherGroup)
            .hasNoService(sandbox2.loadClass(QUERY_CLASS))
            .hasService(sandbox1.loadClass(QUERY_CLASS))
            .hasService(ServiceComponentRuntime::class.java)
            .hasService(Resolver::class.java)
            .hasService(sandbox1.loadClass(SERVICES1_FLOW_CLASS))
            .hasService(sandbox2.loadClass(SERVICES2_FLOW_CLASS))
    }

    @Test
    fun testServicesForCPK2() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val sandbox1 = thisGroup.getSandbox(sandboxLoader.cpk1.id)
        val sandbox2 = thisGroup.getSandbox(sandboxLoader.cpk2.id)
        val serviceClasses = runFlow(SERVICES2_FLOW_CLASS, thisGroup).onEach(::println)

        // CPK2 should be able to see its own services, and any
        // services inside CPK1's "main" jar, but nothing from CPK3.
        assertThat(serviceClasses)
            .hasNoServiceFromGroup(otherGroup)
            .hasNoService(sandbox1.loadClass(QUERY_CLASS))
            .hasService(sandbox2.loadClass(QUERY_CLASS))
            .hasService(ServiceComponentRuntime::class.java)
            .hasService(Resolver::class.java)
            .hasService(sandbox1.loadClass(SERVICES1_FLOW_CLASS))
            .hasService(sandbox2.loadClass(SERVICES2_FLOW_CLASS))
    }

    @Test
    fun testServicesForCPK3() {
        val thisGroup = sandboxLoader.group2
        val otherGroup = sandboxLoader.group1
        val sandbox3 = thisGroup.getSandbox(sandboxLoader.cpk3.id)
        val serviceClasses = runFlow(SERVICES3_FLOW_CLASS, thisGroup).onEach(::println)

        // CPK3 should be able to see its own services, but
        // no service belonging to either CPK1 or CPK2.
        assertThat(serviceClasses)
            .hasNoServiceFromGroup(otherGroup)
            .hasService(sandbox3.loadClass(QUERY_CLASS))
            .hasService(ServiceComponentRuntime::class.java)
            .hasService(Resolver::class.java)
            .hasService(sandbox3.loadClass(SERVICES3_FLOW_CLASS))
    }
}
