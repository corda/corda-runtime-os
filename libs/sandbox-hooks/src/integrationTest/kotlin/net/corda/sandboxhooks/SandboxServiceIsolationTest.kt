package net.corda.sandboxhooks

import net.corda.sandbox.SandboxGroup
import org.junit.jupiter.api.Assertions.assertTrue
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

        /** Checks whether [sandboxGroup] contains [clazz]. */
        fun containsClass(sandboxGroup: SandboxGroup, clazz: Class<*>): Boolean {
            val bundle = FrameworkUtil.getBundle(clazz) ?: return false
            return sandboxLoader.containsBundle(sandboxGroup, bundle)
        }
    }

    /** Checks whether none of the [foundServices] are contained in the [sandboxGroup]. */
    private fun hasNoServiceFromGroup(foundServices: Collection<Class<*>>, sandboxGroup: SandboxGroup) =
        foundServices.none { service -> containsClass(sandboxGroup, service) }

    /** Checks whether the [foundServices] contains all of the [serviceClasses]. */
    private fun hasAllServices(foundServices: Collection<Class<*>>, serviceClasses: Collection<Class<*>>) =
        serviceClasses.all { serviceClass ->
            foundServices.any { service -> serviceClass.isAssignableFrom(service) }
        }

    /** Checks whether the [foundServices] contains none of the [serviceClasses]. */
    private fun hasNoneServices(foundServices: Collection<Class<*>>, serviceClasses: Collection<Class<*>>) =
        serviceClasses.all { serviceClass ->
            foundServices.none { service -> serviceClass.isAssignableFrom(service) }
        }

    @Test
    fun `sandbox can see its own services and main bundle services in the same sandbox group only`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val serviceClasses = sandboxLoader.runFlow<List<Class<*>>>(SERVICES_FLOW_CPK_1, thisGroup)

        // CPK 1 can see public services, services in its own sandbox, and services in the main bundle of other
        // sandboxes in the same group.
        assertTrue(
            hasAllServices(
                serviceClasses, setOf(
                    ServiceComponentRuntime::class.java,
                    Resolver::class.java,
                    sandboxLoader.sandbox1.loadClassFromCordappBundle(LIBRARY_QUERY_CLASS),
                    sandboxLoader.sandbox1.loadClassFromCordappBundle(SERVICES_FLOW_CPK_1),
                    sandboxLoader.sandbox2.loadClassFromCordappBundle(SERVICES_FLOW_CPK_2)
                )
            )
        )

        // CPK 1 cannot see any services from another sandbox group.
        assertTrue(hasNoServiceFromGroup(serviceClasses, otherGroup))

        // CPK 1 cannot see any library services from another sandbox in the same sandbox group.
        assertTrue(
            hasNoneServices(
                serviceClasses,
                setOf(sandboxLoader.sandbox2.loadClassFromCordappBundle(LIBRARY_QUERY_CLASS))
            )
        )
    }
}