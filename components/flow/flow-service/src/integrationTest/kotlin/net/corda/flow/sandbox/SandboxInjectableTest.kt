package net.corda.flow.sandbox

import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
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
import java.util.UUID

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class SandboxInjectableTest {
    companion object {
        private const val CPB_INJECT = "flow-worker-dev-package.cpb"
        private const val FLOW_CLASS_NAME = "net.corda.flowworker.development.flows.MessagingFlow"
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        private val holdingIdentity = HoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var sandboxFactory: SandboxFactory

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
            sandboxFactory = setup.fetchService(timeout = 1000)
        }
    }

    @Test
    fun testCordaInjectables() {
        val vnodeInfo = sandboxFactory.loadVirtualNode(CPB_INJECT, holdingIdentity)
        try {
            sandboxFactory.getOrCreateSandbox(holdingIdentity).use { sandboxContext ->
                val sandbox = sandboxContext.sandboxGroup
                val flowClass = sandbox.loadClassFromMainBundles(FLOW_CLASS_NAME)
                val flowBundle = FrameworkUtil.getBundle(flowClass)
                val flowContext = flowBundle.bundleContext

                @Suppress("unchecked_cast")
                val references = flowContext.getServiceReferences(
                    SingletonSerializeAsToken::class.java.name, CORDA_SANDBOX_FILTER
                ) as? Array<ServiceReference<out SingletonSerializeAsToken>>
                    ?: fail("No sandbox services found")
                assertThat(references).hasSize(4)

                assertAllCordaSingletons(references)

                val serviceClasses: List<Class<*>> = references.mapNotNull { ref ->
                    flowContext.getService(ref)?.also {
                        flowContext.ungetService(ref)
                    }
                }.map(Any::javaClass)
                assertThat(serviceClasses)
                    .allSatisfy { serviceClass ->
                        assertThat(SingletonSerializeAsToken::class.java).isAssignableFrom(serviceClass)
                    }
            }
        } finally {
            sandboxFactory.unloadVirtualNode(vnodeInfo)
        }
    }

    private fun assertAllCordaSingletons(references: Array<ServiceReference<out SingletonSerializeAsToken>>) {
        assertThat(references).allSatisfy { reference ->
            assertThat(reference.getProperty(SERVICE_SCOPE)).isEqualTo(SCOPE_SINGLETON)
            assertThat(reference.getProperty(CORDA_SANDBOX)).isEqualTo(true)
        }
    }
}
