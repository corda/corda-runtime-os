package net.corda.sandboxhooks

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
    }

    @Test
    fun `sandbox can see services from its own sandbox and main bundles in the same sandbox group`() {
        val thisGroup = sandboxLoader.group1
        // This flow returns all services visible to this bundle.
        val serviceClasses = runFlow<List<Class<*>>>(thisGroup, SERVICES_FLOW_CPK_1)

        val expectedServices = setOf(
            ServiceComponentRuntime::class.java,
            Resolver::class.java,
            sandboxLoader.group1.loadClassFromMainBundle(sandboxLoader.cpk1.metadata.id, LIBRARY_QUERY_CLASS),
            sandboxLoader.group1.loadClassFromMainBundle(sandboxLoader.cpk1.metadata.id, SERVICES_FLOW_CPK_1),
            sandboxLoader.group1.loadClassFromMainBundle(sandboxLoader.cpk2.metadata.id, SERVICES_FLOW_CPK_2)
        )

        assertTrue(
            expectedServices.all { serviceClass ->
                serviceClasses.any { service -> serviceClass.isAssignableFrom(service) }
            }
        )
    }

    @Test
    fun `sandbox cannot see services in library bundles of other sandboxes or in main bundles of other sandbox groups`() {
        val thisGroup = sandboxLoader.group1
        val otherGroup = sandboxLoader.group2
        val serviceClasses = runFlow<List<Class<*>>>(thisGroup, SERVICES_FLOW_CPK_1)

        assertTrue(serviceClasses.none { service ->
            val serviceBundle = FrameworkUtil.getBundle(service)
            if (serviceBundle != null) {
                sandboxGroupContainsBundle(otherGroup, serviceBundle)
            } else {
                false
            }
        })
    }
}