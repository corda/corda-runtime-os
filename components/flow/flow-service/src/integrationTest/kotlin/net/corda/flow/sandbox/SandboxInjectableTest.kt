package net.corda.flow.sandbox

import java.nio.file.Path
import java.util.UUID
import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.v5.crypto.DigestService
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.SCOPE_SINGLETON
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class SandboxInjectableTest {
    companion object {
        private const val CPB_INJECT = "sandbox-cpk-inject-package.cpb"
        private const val FLOW_CLASS_NAME = "com.example.sandbox.cpk.inject.ExampleFlow"
        private const val SERVICE_ONE_CLASS_NAME = "com.example.sandbox.cpk.inject.ExampleServiceOne"
        private const val SERVICE_TWO_CLASS_NAME = "com.example.sandbox.cpk.inject.impl.ExampleServiceTwo"
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        private val holdingIdentity = HoldingIdentity(X500_NAME, UUID.randomUUID().toString())

        @InjectService(timeout = 1000)
        lateinit var sandboxSetup: SandboxSetup

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir baseDirectory: Path) {
            sandboxSetup.configure(baseDirectory)
        }
    }

    @InjectService(timeout = 1000)
    lateinit var sandboxLoader: SandboxLoader

    @Test
    fun testCordaInjectables() {
        val vnodeInfo = sandboxLoader.loadCPI(CPB_INJECT, holdingIdentity)
        try {
            sandboxLoader.getOrCreateSandbox(holdingIdentity).use { sandboxContext ->
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

                val digestService = serviceClasses - setOf(serviceOneClass, serviceTwoClass)
                assertThat(digestService).hasSize(1)
                assertThat(DigestService::class.java).isAssignableFrom(digestService.single())
            }
        } finally {
            sandboxLoader.unloadCPI(vnodeInfo)
        }
    }

    private fun assertAllCordaSingletons(references: Array<ServiceReference<out SingletonSerializeAsToken>>) {
        assertThat(references).allSatisfy { reference ->
            assertThat(reference.getProperty(SERVICE_SCOPE)).isEqualTo(SCOPE_SINGLETON)
            assertThat(reference.getProperty(CORDA_SANDBOX)).isEqualTo(true)
        }
    }
}
