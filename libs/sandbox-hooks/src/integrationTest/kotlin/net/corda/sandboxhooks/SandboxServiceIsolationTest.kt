package net.corda.sandboxhooks

import net.corda.sandbox.SandboxGroup
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.ObjectAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.service.resolver.Resolver
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of services across sandbox groups. */
@ExtendWith(ServiceExtension::class)
class SandboxServiceIsolationTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        fun assertThat(classes: List<Class<out Any>>) = ServiceClassListAssertions(classes)

        class ServiceClassListAssertions(classes: List<Class<out Any>>)
            : AbstractListAssert<ServiceClassListAssertions, List<Class<out Any>>, Class<out Any>, ObjectAssert<Class<out Any>>>(classes, ServiceClassListAssertions::class.java) {
            override fun toAssert(value: Class<out Any>?, description: String?) = ObjectAssert(value!!) // Never called.

            override fun newAbstractIterableAssert(iterable: Iterable<Class<out Any>>): ServiceClassListAssertions {
                return ServiceClassListAssertions(iterable as List<Class<out Any>>)
            }

            fun hasNoServiceFromGroup(group: SandboxGroup): ServiceClassListAssertions {
                return noneMatch { svc -> containsClass(svc, group) }
            }

            fun hasNoService(serviceClass: Class<*>): ServiceClassListAssertions {
                return noneMatch { svc -> serviceClass.isAssignableFrom(svc) }
            }

            fun hasService(serviceClass: Class<*>): ServiceClassListAssertions {
                return anyMatch { svc -> serviceClass.isAssignableFrom(svc) }
            }
        }

        fun containsClass(clazz: Class<*>, group: SandboxGroup): Boolean {
            val bundle = FrameworkUtil.getBundle(clazz) ?: return false
            return sandboxLoader.containsBundle(group, bundle)
        }
    }

    @Test
    fun `sandbox can see its own services and main bundle services in the same sandbox group only`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val serviceClasses = sandboxLoader.runFlow<List<Class<out Any>>>(SERVICES_FLOW_CPK_1, thisGroup)

        // CPK1 should be able to see its own services, and any services inside CPK2's "main" jar, but nothing from CPK3.
        assertThat(serviceClasses)
            .hasNoServiceFromGroup(otherGroup)
            .hasNoService(sandboxLoader.sandbox2.loadClassFromCordappBundle(LIBRARY_QUERY_CLASS))
            .hasService(sandboxLoader.sandbox1.loadClassFromCordappBundle(LIBRARY_QUERY_CLASS))
            .hasService(ServiceComponentRuntime::class.java)
            .hasService(Resolver::class.java)
            .hasService(sandboxLoader.sandbox1.loadClassFromCordappBundle(SERVICES_FLOW_CPK_1))
            .hasService(sandboxLoader.sandbox2.loadClassFromCordappBundle(SERVICES_FLOW_CPK_2))
    }
}