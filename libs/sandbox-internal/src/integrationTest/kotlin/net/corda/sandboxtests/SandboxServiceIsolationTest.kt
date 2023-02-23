package net.corda.sandboxtests

import java.nio.file.Path
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.service.resolver.Resolver
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of services across sandbox groups. */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class SandboxServiceIsolationTest {

    companion object {
        @JvmStatic
        @RegisterExtension
        private val lifecycle = AllTestsLifecycle()
    }

    private lateinit var sandboxFactory: SandboxFactory

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            sandboxFactory = setup.fetchService(timeout = 30000)
        }
    }

    @Test
    fun `sandbox can see services from its own sandbox`() {
        // This flow returns all services visible to this bundle.
        val serviceClasses = runFlow<List<Class<*>>>(sandboxFactory.group1, SERVICES_FLOW_CPK_1)

        val cpk1FlowClass = sandboxFactory.group1.loadClassFromMainBundles(SERVICES_FLOW_CPK_1)
        val expectedServices = setOf(
            cpk1FlowClass,

            // This class belongs to one of CPK1's library bundles,
            // but still has a bundle wiring to the main bundle.
            FrameworkUtil.getBundle(cpk1FlowClass).loadClass(LIBRARY_QUERY_CLASS)
        )

        expectedServices.forEach { serviceClass ->
            assertTrue(serviceClasses.any { service -> serviceClass.isAssignableFrom(service) })
        }
    }

    @Test
    fun `sandbox can see services from main bundles in the same sandbox group`() {
        // This flow returns all services visible to this bundle.
        val serviceClasses = runFlow<List<Class<*>>>(sandboxFactory.group1, SERVICES_FLOW_CPK_1)

        val expectedService = sandboxFactory.group1.loadClassFromMainBundles(SERVICES_FLOW_CPK_2)

        assertTrue(serviceClasses.any { service -> expectedService.isAssignableFrom(service) })
    }

    @Test
    fun `sandbox can see services from public bundles in public sandboxes`() {
        // This flow returns all services visible to this bundle.
        val serviceClasses = runFlow<List<Class<*>>>(sandboxFactory.group1, SERVICES_FLOW_CPK_1)

        val expectedServices = setOf(ServiceComponentRuntime::class.java, Resolver::class.java)

        expectedServices.forEach { serviceClass ->
            assertTrue(serviceClasses.any { service -> serviceClass.isAssignableFrom(service) })
        }
    }

    @Test
    fun `sandbox cannot see services in library bundles of other sandboxes`() {
        val serviceClasses = runFlow<List<Class<*>>>(sandboxFactory.group1, SERVICES_FLOW_CPK_1)

        // We can only see a single implementation of the library class, the one in our own sandbox.
        assertEquals(1, serviceClasses.filter { service -> service.name == LIBRARY_QUERY_IMPL_CLASS }.size)
    }

    @Test
    fun `sandbox cannot see services in main bundles of other sandbox groups`() {
        val serviceClasses = runFlow<List<Class<*>>>(sandboxFactory.group1, SERVICES_FLOW_CPK_1)

        val mainBundleInOtherSandboxGroupService =
            sandboxFactory.group2.loadClassFromMainBundles(SERVICES_FLOW_CPK_3)

        assertFalse(serviceClasses.any { service ->
            mainBundleInOtherSandboxGroupService.isAssignableFrom(service)
        })
    }

    @Test
    fun `sandbox cannot see services in private bundles in public sandboxes`() {
        val serviceClasses = runFlow<List<Class<*>>>(sandboxFactory.group1, SERVICES_FLOW_CPK_1)

        val privateBundleInPublicSandboxServices =
            setOf(SandboxCreationService::class.java, SandboxContextService::class.java)

        privateBundleInPublicSandboxServices.forEach { privateService ->
            assertFalse(serviceClasses.any { service ->
                privateService.isAssignableFrom(service)
            })
        }
    }

    @Test
    fun `two sandboxes in the same group get their own copy of a singleton service defined in both sandboxes' private bundles`() {
        // The counter for each `LibrarySingletonServiceUser` is independent.

        // We increment the counter of CPK 1's `LibrarySingletonService.
        assertEquals(1, runFlow(sandboxFactory.group1, LIB_SINGLETON_SERVICE_FLOW_CPK_1))
        assertEquals(2, runFlow(sandboxFactory.group1, LIB_SINGLETON_SERVICE_FLOW_CPK_1))

        // The counter of CPK 2's `LibrarySingletonService` is unaffected.
        assertEquals(1, runFlow(sandboxFactory.group1, LIB_SINGLETON_SERVICE_FLOW_CPK_2))
    }
}