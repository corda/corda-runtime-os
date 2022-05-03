package net.corda.sandboxgroupcontext.test

import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.crypto.DigestService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SCOPE_SINGLETON
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SandboxInjectableTest {
    companion object {
        private const val CPB_INJECT = "META-INF/sandbox-cpk-inject-package.cpb"
        private const val FLOW_CLASS_NAME = "com.example.sandbox.cpk.inject.ExampleFlow"
        private const val SERVICE_ONE_CLASS_NAME = "com.example.sandbox.cpk.inject.ExampleServiceOne"
        private const val SERVICE_TWO_CLASS_NAME = "com.example.sandbox.cpk.inject.impl.ExampleServiceTwo"
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()
    private lateinit var virtualNode: VirtualNodeService
    private lateinit var sandboxContext: SandboxGroupContext

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        context: BundleContext,
        @TempDir
        baseDirectory: Path
    ) {
        sandboxSetup.configure(context, baseDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(timeout = 1000)
            sandboxContext = virtualNode.loadSandbox(CPB_INJECT)
        }
    }

    @Test
    fun testCordaInjectables() {
        try {
            val sandbox = sandboxContext.sandboxGroup
            val flowClass = sandbox.loadClassFromMainBundles(FLOW_CLASS_NAME)
            val flowBundle = FrameworkUtil.getBundle(flowClass)
            val flowContext = flowBundle.bundleContext
            val serviceOneClass = flowBundle.loadClass(SERVICE_ONE_CLASS_NAME)
            val serviceTwoClass = flowBundle.loadClass(SERVICE_TWO_CLASS_NAME)

            @Suppress("unchecked_cast")
            val references = flowContext.getServiceReferences(
                SingletonSerializeAsToken::class.java.name, CORDA_SANDBOX_FILTER
            ) as? Array<ServiceReference<out SingletonSerializeAsToken>>
                ?: fail("No sandbox services found")
            assertThat(references).hasSize(3)

            assertAllCordaSingletons(references)

            val serviceClasses: List<Class<*>> = references.mapNotNull { ref ->
                flowContext.getService(ref)?.also {
                    flowContext.ungetService(ref)
                }
            }.map(Any::javaClass)
            assertThat(serviceClasses)
                .contains(serviceOneClass, serviceTwoClass)
                .allSatisfy { serviceClass ->
                    assertThat(SingletonSerializeAsToken::class.java).isAssignableFrom(serviceClass)
                }

            // Given the proposed changes to Corda services, this test should no longer be checking if services are
            // loaded correctly - it's likely this API will significantly change. Proof that custom digest services
            // still function is however valid.
            assertThat(serviceClasses.any { DigestService::class.java.isAssignableFrom(it) }).isTrue
        } finally {
            virtualNode.unloadSandbox(sandboxContext)
        }
    }

    private fun assertAllCordaSingletons(references: Array<ServiceReference<out SingletonSerializeAsToken>>) {
        assertThat(references).allSatisfy { reference ->
            assertThat(reference.getProperty(SERVICE_SCOPE)).isEqualTo(SCOPE_SINGLETON)
            assertThat(reference.getProperty(CORDA_SANDBOX)).isEqualTo(true)
        }
    }
}