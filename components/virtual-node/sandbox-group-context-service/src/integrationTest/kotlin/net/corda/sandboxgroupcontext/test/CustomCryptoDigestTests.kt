package net.corda.sandboxgroupcontext.test

import java.nio.file.Path
import java.util.UUID
import net.corda.sandbox.SandboxException
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@Suppress("FunctionName")
class CustomCryptoDigestTests {
    @Suppress("unused")
    companion object {
        private const val DIGEST_ONE_CLASSNAME = "com.example.CryptoConsumer"
        private const val DIGEST_TWO_CLASSNAME = "org.example.CryptoConsumer"

        private const val DIGEST_CPB_ONE = "META-INF/crypto-custom-digest-one-consumer.cpb"
        private const val DIGEST_CPB_TWO = "META-INF/crypto-custom-digest-two-consumer.cpb"

        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        @RegisterExtension
        private val lifecycle = EachTestLifecycle()

        @InjectService(timeout = 1000)
        lateinit var sandboxSetup: SandboxSetup

        private lateinit var virtualNode: VirtualNodeService

        @JvmStatic
        @BeforeAll
        fun setup(@InjectBundleContext bundleContext: BundleContext, @TempDir testDirectory: Path) {
            sandboxSetup.configure(bundleContext, testDirectory)
            lifecycle.accept(sandboxSetup) { setup ->
                virtualNode = setup.fetchService(timeout = 1000)
            }
        }

        private fun generateHoldingIdentity() = HoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }

    private val vnodes = mutableMapOf<SandboxGroupContext, VirtualNodeInfo>()

    private fun loadAndInstantiate(resourceName: String): SandboxGroupContext {
        val vnodeInfo = virtualNode.loadCPI(resourceName, generateHoldingIdentity())
        return virtualNode.getOrCreateSandbox(vnodeInfo).also { ctx ->
            vnodes[ctx] = vnodeInfo
        }
    }

    private fun unloadSandbox(sandboxGroupContext: SandboxGroupContext) {
        (sandboxGroupContext as? AutoCloseable)?.close()
        vnodes.remove(sandboxGroupContext)?.let(virtualNode::unloadCPI)
    }

    @Test
    fun `end to end crypto test for first cordapp as two cpks`() {
        val sandboxGroupContext = loadAndInstantiate(DIGEST_CPB_ONE)
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext)
    }

    @Test
    fun `first consumer cannot use other CPI digest`() {
        val sandboxGroupContext = loadAndInstantiate(DIGEST_CPB_ONE)
        assertThrows<IllegalArgumentException> {
            virtualNode.runFlow<SecureHash>("com.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContext)
        }
    }

    @Test
    fun `end to end crypto test for second cordapp as two cpks`() {
        val sandboxGroupContext = loadAndInstantiate(DIGEST_CPB_TWO)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContext)
    }

    @Test
    fun `second consumer cannot use other CPI digest`() {
        val sandboxGroupContext = loadAndInstantiate(DIGEST_CPB_TWO)
        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' is >> org << not com for 'TWO'.
            virtualNode.runFlow<SecureHash>("org.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContext)
        }
    }

    @Test
    fun `load two cpis side by side`() {
        val sandboxGroupContextOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = loadAndInstantiate(DIGEST_CPB_TWO)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)
    }

    @Test
    fun `load two cpis side by side cannot use each others custom digests`() {
        val sandboxGroupContextOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = loadAndInstantiate(DIGEST_CPB_TWO)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)

        assertThrows<IllegalArgumentException> {
            virtualNode.runFlow<SecureHash>("com.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContextOne)
        }

        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' is >> org << not com in 'TWO'
            virtualNode.runFlow<SecureHash>("org.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContextTwo)
        }
    }

    @Test
    fun `load cpi and then unload cpi`() {
        val sandboxGroupContext = loadAndInstantiate(DIGEST_CPB_ONE)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext)

        unloadSandbox(sandboxGroupContext)

        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext)
        }
    }

    @Test
    fun `load two cpis then unload both`() {
        val sandboxGroupContextOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = loadAndInstantiate(DIGEST_CPB_TWO)

        // We can use the two different custom cryptos
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)

        // explicitly unload sandboxGroupOne
        unloadSandbox(sandboxGroupContextOne)

        // we can still run "two"
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)

        // but not sandboxGroupOne
        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        }

        // explicitly unload sandboxGroupTwo
        unloadSandbox(sandboxGroupContextTwo)

        // and now we cannot run that.
        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)
        }
    }

    /**
     * This test uses the SAME cpi, twice.  This tests some of the sandbox code.
     */
    @Test
    fun `load same cpis twice with different sandboxes then unload both`() {
        val sandboxGroupContextOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = loadAndInstantiate(DIGEST_CPB_ONE)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo)

        // explicitly unwire *and* unload the first sandbox group
        unloadSandbox(sandboxGroupContextOne)

        // But we can still run the same identical custom crypto code in the second sandbox group
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo)

        // But not in the first, because it's been removed.
        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        }

        // Explicitly unwire *and* unload the second sandbox group
        unloadSandbox(sandboxGroupContextTwo)

        // And now we cannot run anything in the second group.
        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo)
        }
    }
}
