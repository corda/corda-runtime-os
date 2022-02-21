package net.corda.sandboxgroupcontext.test

import java.nio.file.Path
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

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class CordaServicesTest {
    companion object {
        private const val SERVICE_FILTER = "(&$CORDA_SANDBOX_FILTER(service.scope=singleton))"
        private const val SERVICES_CPB = "META-INF/corda-services.cpb"

        private const val COMPONENT1_SERVICE_CLASS_NAME = "com.example.service.ComponentOneCordaService"
        private const val COMPONENT2_SERVICE_CLASS_NAME = "com.example.service.ComponentTwoCordaService"
        private const val POJO_SERVICE_CLASS_NAME = "com.example.service.PojoCordaService"
        private const val POJO_WITH_ARGS_SERVICE_CLASS_NAME = "com.example.service.PojoWithArgsCordaService"
        private const val SINGLETON_POJO_SERVICE_CLASS_NAME = "com.example.service.SingletonPojoService"

        private const val DISABLED_COMPONENT_CLASS_NAME = "com.example.service.DisabledComponentCordaService"
        private const val MISCONFIGURED_COMPONENT_CLASS_NAME = "com.example.service.MisconfiguredComponentCordaService"
        private const val BUNDLE_COMPONENT_CLASS_NAME = "com.example.service.BundleComponentCordaService"
        private const val PROTOTYPE_COMPONENT_CLASS_NAME = "com.example.service.PrototypeComponentCordaService"
        private const val SINGLETON_COMPONENT_CLASS_NAME = "com.example.service.SingletonComponentService"

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
    fun testCordaServicePojoIsRegistered() {
        assertAllMainBundlesSeeService(POJO_SERVICE_CLASS_NAME)
    }

    @Test
    fun testCordaServiceComponentsAreRegistered() {
        assertAllMainBundlesSeeService(COMPONENT1_SERVICE_CLASS_NAME)
        assertAllMainBundlesSeeService(COMPONENT2_SERVICE_CLASS_NAME)
    }

    @Test
    fun testDisabledServiceIsNotRegistered() {
        assertNoMainBundlesSeeService(DISABLED_COMPONENT_CLASS_NAME)
    }

    @Test
    fun testServiceWithoutOwnClassNotRegistered() {
        assertNoMainBundlesSeeService(MISCONFIGURED_COMPONENT_CLASS_NAME)
    }

    @Test
    fun testNonSingletonScopesNotRegistered() {
        assertNoMainBundlesSeeService(BUNDLE_COMPONENT_CLASS_NAME)
        assertNoMainBundlesSeeService(PROTOTYPE_COMPONENT_CLASS_NAME)
    }

    @Test
    fun testPojoWithNonDefaultConstructorNotRegistered() {
        assertNoMainBundlesSeeService(POJO_WITH_ARGS_SERVICE_CLASS_NAME)
    }

    @Test
    fun testServiceWithOnlySingletonSerializeAsTokenIsNotEnough() {
        assertNoMainBundlesSeeService(SINGLETON_COMPONENT_CLASS_NAME)
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

    private fun assertAllMainBundlesSeeService(serviceClassName: String) {
        assertServiceClassExists(serviceClassName)
        assertAll(cpkBundleContexts.map { bundleContext -> {
            assertHasService(bundleContext, serviceClassName)
        } })
    }

    private fun assertHasService(bundleContext: BundleContext, serviceClassName: String) {
        assertNotNull(
            bundleContext.getServiceReferences(serviceClassName, SERVICE_FILTER),
            "Bundle ${bundleContext.bundle.symbolicName} does not have service $serviceClassName"
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
