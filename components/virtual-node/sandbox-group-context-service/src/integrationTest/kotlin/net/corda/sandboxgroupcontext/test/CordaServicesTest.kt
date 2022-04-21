package net.corda.sandboxgroupcontext.test

import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

/**
 * The Corda service API is scheduled for large scale changes, and as a result these tests are no longer necessarily
 * valid. The current API prevents these from passing, but as this may be removed entirely these tests may also be
 * scheduled for deletion.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class CordaServicesTest {
    companion object {
        private const val SERVICE_FILTER = "(&$CORDA_SANDBOX_FILTER(service.scope=singleton))"
        private const val SERVICES_CPB = "META-INF/corda-services.cpb"

        private const val SINGLETON_POJO_SERVICE_CLASS_NAME = "com.example.service.SingletonPojoService"

        private const val HOST_CLASS_NAME = "com.example.service.host.CordaServiceHost"
    }

    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var sandboxGroupContext: SandboxGroupContext

    private val cpkBundleContexts: Set<BundleContext>
        get() = sandboxGroupContext.sandboxGroup.metadata.keys.mapTo(LinkedHashSet(), Bundle::getBundleContext)

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
            virtualNode = setup.fetchService(timeout = 1000)
            sandboxGroupContext = virtualNode.loadSandbox(SERVICES_CPB)
        }
    }

    @Test
    fun testServiceWithOnlySingletonSerializeAsTokenIsNotEnough() {
        assertNoMainBundlesSeeService(SINGLETON_POJO_SERVICE_CLASS_NAME)
    }

    @Test
    fun testHostServiceAvailable() {
        assertAll(cpkBundleContexts.map { bundleContext -> {
            assertHasHostService(bundleContext)
        } })
    }

    private fun assertServiceClassExists(serviceClassName: String) {
        assertNotNull(
            sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(serviceClassName),
            "Service $serviceClassName not found in main bundles."
        )
    }

    private fun assertNoMainBundlesSeeService(serviceClassName: String) {
        // Check that the class actually exists to be found.
        assertServiceClassExists(serviceClassName)

        // Now check that no main bundle has a service of this type.
        assertAll(cpkBundleContexts.map { bundleContext -> {
            assertDoesNotHaveService(bundleContext, serviceClassName)
        } })
    }

    private fun assertDoesNotHaveService(bundleContext: BundleContext, serviceClassName: String) {
        assertNull(
            bundleContext.getServiceReferences(serviceClassName, SERVICE_FILTER),
            "Bundle ${bundleContext.bundle.symbolicName} has unwanted service $serviceClassName"
        )
    }

    private fun assertHasHostService(bundleContext: BundleContext) {
        val references = bundleContext.getServiceReferences(Runnable::class.java, "(component.name=$HOST_CLASS_NAME)")
            ?: fail("Runnable service $HOST_CLASS_NAME not found.")
        assertThat(references).hasSize(1)

        val reference = references.single()
        bundleContext.getService(reference)?.also { host ->
            try {
                assertThat(host::class.java.name)
                    .isEqualTo(HOST_CLASS_NAME)
                host.run()
            } finally {
                bundleContext.ungetService(reference)
            }
        } ?: fail("Instance of $HOST_CLASS_NAME not found.")
    }
}
