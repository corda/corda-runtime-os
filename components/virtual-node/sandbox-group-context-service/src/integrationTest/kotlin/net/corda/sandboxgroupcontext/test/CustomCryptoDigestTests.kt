package net.corda.sandboxgroupcontext.test

import java.nio.file.Path
import net.corda.sandbox.SandboxException
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
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
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class CustomCryptoDigestTests {
    companion object {
        private const val PLATFORM_DIGEST_ONE_CLASSNAME = "com.example.PlatformCryptoConsumer"
        private const val DIGEST_ONE_CLASSNAME = "com.example.CryptoConsumer"
        private const val DIGEST_TWO_CLASSNAME = "org.example.CryptoConsumer"
        private const val ALGORITHM_PROPERTY_NAME = "algorithm"

        private const val DIGEST_CPB_ONE = "META-INF/crypto-custom-digest-one-consumer.cpb"
        private const val DIGEST_CPB_TWO = "META-INF/crypto-custom-digest-two-consumer.cpb"
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService

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
        }
    }

    @Test
    fun `end to end crypto test for first cordapp as two cpks`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_ONE)
        assertThat(virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext))
            .hasFieldOrPropertyWithValue(ALGORITHM_PROPERTY_NAME, "SHA-256-TRIPLE")
    }

    @Test
    fun `first consumer cannot use other CPI digest`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_ONE)
        assertThrows<IllegalArgumentException> {
            virtualNode.runFlow<SecureHash>("com.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContext)
        }
    }

    @Test
    fun `end to end crypto test for second cordapp as two cpks`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_TWO)
        assertThat(virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContext))
            .hasFieldOrPropertyWithValue(ALGORITHM_PROPERTY_NAME, "SHA-256-QUAD")
    }

    @Test
    fun `second consumer cannot use other CPI digest`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_TWO)
        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' is >> org << not com for 'TWO'.
            virtualNode.runFlow<SecureHash>("org.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContext)
        }
    }

    @Test
    fun `check consumer can still use platform crypto`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_ONE)
        assertThat(virtualNode.runFlow<SecureHash>(PLATFORM_DIGEST_ONE_CLASSNAME, sandboxGroupContext))
            .hasFieldOrPropertyWithValue(ALGORITHM_PROPERTY_NAME, "SHA-256")
    }

    @Test
    fun `load two cpis side by side`() {
        val sandboxGroupContextOne = virtualNode.loadSandbox(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = virtualNode.loadSandbox(DIGEST_CPB_TWO)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)
    }

    @Test
    fun `load two cpis side by side cannot use each others custom digests`() {
        val sandboxGroupContextOne = virtualNode.loadSandbox(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = virtualNode.loadSandbox(DIGEST_CPB_TWO)

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
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_ONE)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext)

        virtualNode.unloadSandbox(sandboxGroupContext)

        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext)
        }
    }

    @Test
    fun `load two cpis then unload both`() {
        val sandboxGroupContextOne = virtualNode.loadSandbox(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = virtualNode.loadSandbox(DIGEST_CPB_TWO)

        // We can use the two different custom cryptos
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)

        // explicitly unload sandboxGroupOne
        virtualNode.unloadSandbox(sandboxGroupContextOne)

        // we can still run "two"
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)

        // but not sandboxGroupOne
        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        }

        // explicitly unload sandboxGroupTwo
        virtualNode.unloadSandbox(sandboxGroupContextTwo)

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
        val sandboxGroupContextOne = virtualNode.loadSandbox(DIGEST_CPB_ONE)
        val sandboxGroupContextTwo = virtualNode.loadSandbox(DIGEST_CPB_ONE)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo)

        // explicitly unwire *and* unload the first sandbox group
        virtualNode.unloadSandbox(sandboxGroupContextOne)

        // But we can still run the same identical custom crypto code in the second sandbox group
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo)

        // But not in the first, because it's been removed.
        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        }

        // Explicitly unwire *and* unload the second sandbox group
        virtualNode.unloadSandbox(sandboxGroupContextTwo)

        // And now we cannot run anything in the second group.
        assertThrows<SandboxException> {
            virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo)
        }
    }
}
