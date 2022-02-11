package net.corda.virtualnode.manager.test

import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class CustomCryptoDigestTests {

    @Suppress("unused")
    companion object {
        const val DIGEST_ONE_CLASSNAME = "com.example.CryptoConsumer"
        const val DIGEST_TWO_CLASSNAME = "org.example.CryptoConsumer"

        const val DIGEST_CPB_ONE = "META-INF/crypto-custom-digest-one-consumer-cpk.cpb"
        const val DIGEST_CPB_TWO = "META-INF/crypto-custom-digest-two-consumer-cpk.cpb"

        @InjectService(timeout = 1000)
        lateinit var sandboxSetup: SandboxSetup

        @JvmStatic
        @BeforeAll
        fun setup(@InjectBundleContext bundleContext: BundleContext, @TempDir testDirectory: Path) {
            sandboxSetup.configure(bundleContext, testDirectory, setOf("net.corda.crypto-impl"))
        }

        @JvmStatic
        @AfterAll
        fun done() {
            sandboxSetup.shutdown()
        }
    }

    @InjectService(timeout = 1500)
    lateinit var service: IntegrationTestService

    private val sandboxGroupsPerTest = mutableListOf<SandboxGroup>()

    /**
     * After each test is run unload their sandboxes from the system.
     * This involves stopping the bundle and the sandbox creation service removing
     * sandboxes from internal data structures.
     *
     * If we don't clean up, it leaves the sandbox creation service holding references
     * to bundles loaded (CPIs...) in previous tests.  (As it turns out, that doesn't
     * matter, as that's exactly what OSGi protects us from/provides us with - the same bundle
     * loaded multiple times as isolated separate instances).
     */
    @AfterEach
    private fun teardown() {
        sandboxGroupsPerTest.forEach(service::unloadSandboxGroup)
        sandboxGroupsPerTest.clear()
    }

    private fun loadAndInstantiate(resourceDigestCpi: String): SandboxGroup {
        //  "Install" -  the CPKs 'into the system', i.e. copy cpk to disk somewhere and scan the manifests.
        val cpi = service.loadCPIFromResource(resourceDigestCpi)

        //  Instantiate -  load the cpi (its cpks) into the process, and allow OSGi to wire up things.
        val sandboxGroup = service.createSandboxGroupFor(cpi.cpks.toSet())

        // "preExecute" or "postInstantiate" "configure" up various per-CPI services.
        service.registerCrypto(sandboxGroup)

        sandboxGroupsPerTest.add(sandboxGroup)

        return sandboxGroup
    }

    private fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        service.unloadSandboxGroup(sandboxGroup)
        sandboxGroupsPerTest.remove(sandboxGroup)
    }

    /**
     *************  Tests  **************8
     */
    @Test
    fun `end to end crypto test for first cordapp as two cpks`() {
        val sandboxGroup = loadAndInstantiate(DIGEST_CPB_ONE)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroup)
    }

    @Test
    fun `first consumer cannot use other CPI digest`() {
        val sandboxGroup = loadAndInstantiate(DIGEST_CPB_ONE)

        assertThrows<IllegalArgumentException> {
            service.runFlow<SecureHash>("com.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroup)
        }
    }

    @Test
    fun `end to end crypto test for second cordapp as two cpks`() {
        val sandboxGroup = loadAndInstantiate(DIGEST_CPB_TWO)
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroup)
    }

    @Test
    fun `second consumer cannot use other CPI digest`() {
        val sandboxGroup = loadAndInstantiate(DIGEST_CPB_TWO)

        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' is >> org << not com for 'TWO'.
            service.runFlow<SecureHash>("org.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroup)
        }
    }

    @Test
    fun `load two cpis side by side`() {
        val sandboxGroupOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(DIGEST_CPB_TWO)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)
    }

    @Test
    fun `load two cpis side by side cannot use each others custom digests`() {
        val sandboxGroupOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(DIGEST_CPB_TWO)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)

        assertThrows<IllegalArgumentException> {
            service.runFlow<SecureHash>("com.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupOne)
        }

        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' is >> org << not com in 'TWO'
            service.runFlow<SecureHash>("org.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroupTwo)
        }
    }

    @Test
    fun `load cpi and then unload cpi`() {
        val sandboxGroup = loadAndInstantiate(DIGEST_CPB_ONE)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroup)

        unloadSandboxGroup(sandboxGroup)

        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroup)
        }
    }

    @Test
    fun `load two cpis then unload both`() {
        val sandboxGroupOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(DIGEST_CPB_TWO)

        // We can use the two different custom cryptos
        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)

        // explicitly unload sandboxGroupOne
        unloadSandboxGroup(sandboxGroupOne)

        // we can still run "two"
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)

        // but not sandboxGroupOne
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        }

        // explicitly unload sandboxGroupTwo
        unloadSandboxGroup(sandboxGroupTwo)

        // and now we cannot run that.
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)
        }
    }

    /**
     * This test uses the SAME cpi, twice.  This tests some of the sandbox code.
     */
    @Test
    fun `load same cpis twice with different sandboxes then unload both`() {
        val sandboxGroupOne = loadAndInstantiate(DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(DIGEST_CPB_ONE)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupTwo)

        // explicitly unwire *and* unload the first sandbox group
        unloadSandboxGroup(sandboxGroupOne)

        // But we can still run the same identical custom crypto code in the second sandbox group
        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupTwo)

        // But not in the first, because it's been removed.
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        }

        // Explicitly unwire *and* unload the second sandbox group
        unloadSandboxGroup(sandboxGroupTwo)

        // And now we cannot run anything in the second group.
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupTwo)
        }
    }
}
