package net.corda.sandboxgroupcontext.test

import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
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
        private const val TIMEOUT_MILLIS = 10000L
        private const val PLATFORM_DIGEST_ONE_CLASSNAME = "com.example.PlatformCryptoConsumer"
        private const val PACKAGE_NAME_ONE="com.example"
        private const val PACKAGE_NAME_TWO="org.example"
        private const val DIGEST_ONE_CLASSNAME = "$PACKAGE_NAME_ONE.CryptoConsumer"
        private const val DIGEST_TWO_CLASSNAME = "$PACKAGE_NAME_TWO.CryptoConsumer"
        private const val ALGORITHM_PROPERTY_NAME = "algorithm"

        private const val DIGEST_CPB_ONE = "META-INF/crypto-custom-digest-one-consumer.cpb"
        private const val DIGEST_CPB_TWO = "META-INF/crypto-custom-digest-two-consumer.cpb"
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    @Test
    fun `end to end crypto test for first cordapp as two cpks`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW)
        assertThat(virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext))
            .hasFieldOrPropertyWithValue(ALGORITHM_PROPERTY_NAME, "SHA-256-TRIPLE")
    }

    @Test
    fun `first consumer cannot use other CPI digest`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW)
        assertThrows<IllegalArgumentException> {
            virtualNode.runFlow<SecureHash>("$PACKAGE_NAME_ONE.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContext)
        }
    }

    @Test
    fun `end to end crypto test for second cordapp as two cpks`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_TWO, SandboxGroupType.FLOW)
        assertThat(virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContext))
            .hasFieldOrPropertyWithValue(ALGORITHM_PROPERTY_NAME, "SHA-256-QUAD")
    }

    @Test
    fun `second consumer cannot use other CPI digest`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_TWO, SandboxGroupType.FLOW)
        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' is 'TWO'.
            virtualNode.runFlow<SecureHash>("$PACKAGE_NAME_TWO.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContext)
        }
    }

    @Test
    fun `check consumer can still use platform crypto`() {
        val sandboxGroupContext = virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW)
        assertThat(virtualNode.runFlow<SecureHash>(PLATFORM_DIGEST_ONE_CLASSNAME, sandboxGroupContext))
            .hasFieldOrPropertyWithValue(ALGORITHM_PROPERTY_NAME, "SHA-256")
    }

    @Test
    fun `load two cpis side by side`() {
        val sandboxGroupContextOne = virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW)
        val sandboxGroupContextTwo = virtualNode.loadSandbox(DIGEST_CPB_TWO, SandboxGroupType.FLOW)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)
    }

    @Test
    fun `load two cpis side by side cannot use each others custom digests`() {
        val sandboxGroupContextOne = virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW)
        val sandboxGroupContextTwo = virtualNode.loadSandbox(DIGEST_CPB_TWO, SandboxGroupType.FLOW)

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne)
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo)

        assertThrows<IllegalArgumentException> {
            virtualNode.runFlow<SecureHash>("$PACKAGE_NAME_ONE.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContextOne)
        }

        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' 'TWO'
            virtualNode.runFlow<SecureHash>("$PACKAGE_NAME_TWO.CryptoConsumerTryAndUseOtherDigest", sandboxGroupContextTwo)
        }
    }

    @Timeout(30, unit = SECONDS)
    @Test
    fun `load cpi and then unload cpi`() {
        val sandboxGroupContext = mutableListOf(virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW))

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContext.single())

        val completion = virtualNode.releaseSandbox(sandboxGroupContext.single())?: fail("Sandbox missing")
        sandboxGroupContext.clear()
        virtualNode.unloadSandbox(completion)
    }

    @Timeout(30, unit = SECONDS)
    @Test
    fun `load two cpis then unload both`() {
        val sandboxGroupContextOne = mutableListOf(virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW))
        val sandboxGroupContextTwo = mutableListOf(virtualNode.loadSandbox(DIGEST_CPB_TWO, SandboxGroupType.FLOW))

        // We can use the two different custom cryptos
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne.single())
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo.single())

        // explicitly unload sandboxGroupOne
        val completion1 = virtualNode.releaseSandbox(sandboxGroupContextOne.single()) ?: fail("Sandbox1 missing")
        sandboxGroupContextOne.clear()
        virtualNode.unloadSandbox(completion1)

        // we can still run "two"
        virtualNode.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupContextTwo.single())

        // explicitly unload sandboxGroupTwo
        val completion2 = virtualNode.releaseSandbox(sandboxGroupContextTwo.single()) ?: fail("Sandbox2 missing")
        sandboxGroupContextTwo.clear()
        virtualNode.unloadSandbox(completion2)
    }

    /**
     * This test uses the SAME cpi, twice.  This tests some of the sandbox code.
     */
    @Timeout(30, unit = SECONDS)
    @Test
    fun `load same cpis twice with different sandboxes then unload both`() {
        val sandboxGroupContextOne = mutableListOf(virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW))
        val sandboxGroupContextTwo = mutableListOf(virtualNode.loadSandbox(DIGEST_CPB_ONE, SandboxGroupType.FLOW))

        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextOne.single())
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo.single())

        // explicitly unwire *and* unload the first sandbox group
        val completion1 = virtualNode.releaseSandbox(sandboxGroupContextOne.single()) ?: fail("Sandbox1 missing")
        sandboxGroupContextOne.clear()
        virtualNode.unloadSandbox(completion1)

        // But we can still run the same identical custom crypto code in the second sandbox group
        virtualNode.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupContextTwo.single())

        // Explicitly unwire *and* unload the second sandbox group
        val completion2 = virtualNode.releaseSandbox(sandboxGroupContextTwo.single()) ?: fail("Sandbox2 missing")
        sandboxGroupContextTwo.clear()
        virtualNode.unloadSandbox(completion2)
    }
}
