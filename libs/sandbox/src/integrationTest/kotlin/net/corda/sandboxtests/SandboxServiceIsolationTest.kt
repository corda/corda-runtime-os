package net.corda.sandboxtests

import net.corda.sandbox.SandboxCreationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.service.cm.ConfigurationAdmin
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
    fun `sandbox can see services from its own sandbox`() {
        // This flow returns all services visible to this bundle.
        val serviceClasses = runFlow<List<Class<*>>>(sandboxLoader.group1, SERVICES_FLOW_CPK_1)

        val expectedServices = setOf(
            sandboxLoader.group1.loadClassFromMainBundles(LIBRARY_QUERY_CLASS, Any::class.java),
            sandboxLoader.group1.loadClassFromMainBundles(SERVICES_FLOW_CPK_1, Any::class.java)
        )

        expectedServices.forEach { serviceClass ->
            assertTrue(serviceClasses.any { service -> serviceClass.isAssignableFrom(service) })
        }
    }

    @Test
    fun `sandbox can see services from main bundles in the same sandbox group`() {
        // This flow returns all services visible to this bundle.
        val serviceClasses = runFlow<List<Class<*>>>(sandboxLoader.group1, SERVICES_FLOW_CPK_1)

        val expectedService = sandboxLoader.group1.loadClassFromMainBundles(SERVICES_FLOW_CPK_2, Any::class.java)

        assertTrue(serviceClasses.any { service -> expectedService.isAssignableFrom(service) })
    }

    @Test
    fun `sandbox can see services from public bundles in public sandboxes`() {
        // This flow returns all services visible to this bundle.
        val serviceClasses = runFlow<List<Class<*>>>(sandboxLoader.group1, SERVICES_FLOW_CPK_1)

        val expectedServices = setOf(ServiceComponentRuntime::class.java, Resolver::class.java)

        expectedServices.forEach { serviceClass ->
            assertTrue(serviceClasses.any { service -> serviceClass.isAssignableFrom(service) })
        }
    }

    @Test
    fun `sandbox cannot see services in library bundles of other sandboxes`() {
        val serviceClasses = runFlow<List<Class<*>>>(sandboxLoader.group1, SERVICES_FLOW_CPK_1)

        // We can only see a single implementation of the library class, the one in our own sandbox.
        assertEquals(1, serviceClasses.filter { service -> service.name == LIBRARY_QUERY_IMPL_CLASS }.size)
    }

    @Test
    fun `sandbox cannot see services in main bundles of other sandbox groups`() {
        val serviceClasses = runFlow<List<Class<*>>>(sandboxLoader.group1, SERVICES_FLOW_CPK_1)

        val mainBundleInOtherSandboxGroupService =
            sandboxLoader.group2.loadClassFromMainBundles(SERVICES_FLOW_CPK_3, Any::class.java)

        assertFalse(serviceClasses.any { service ->
            mainBundleInOtherSandboxGroupService.isAssignableFrom(service)
        })
    }

    @Test
    fun `sandbox cannot see services in private bundles in public sandboxes`() {
        val serviceClasses = runFlow<List<Class<*>>>(sandboxLoader.group1, SERVICES_FLOW_CPK_1)

        val privateBundleInPublicSandboxServices =
            setOf(SandboxCreationService::class.java, ConfigurationAdmin::class.java)

        privateBundleInPublicSandboxServices.forEach { privateService ->
            assertFalse(serviceClasses.any { service ->
                privateService.isAssignableFrom(service)
            })
        }
    }
}