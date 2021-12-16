package net.corda.virtualnode.manager.test

import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.manager.api.RuntimeRegistration
import net.corda.virtualnode.manager.test.Constants.BASE_DIRECTORY_KEY
import net.corda.virtualnode.manager.test.Constants.BLACKLISTED_KEYS_KEY
import net.corda.virtualnode.manager.test.Constants.PLATFORM_VERSION_KEY
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.Hashtable

@ExtendWith(ServiceExtension::class)
class CustomCryptoDigestTests {

    companion object {
        const val DIGEST_ONE_CLASSNAME = "com.example.CryptoConsumer"
        const val DIGEST_TWO_CLASSNAME = "org.example.CryptoConsumer"

        @InjectService
        lateinit var configAdmin: ConfigurationAdmin

        @InjectService
        lateinit var sandboxCreationService: SandboxCreationService

        @InjectService
        lateinit var connector: RuntimeRegistration

        lateinit var service: IntegrationTestService

        @JvmStatic
        @BeforeAll
        fun setup(@TempDir testDirectory: Path) {
            configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
                val properties = Hashtable<String, Any>()
                properties[BASE_DIRECTORY_KEY] = testDirectory.toString()
                properties[BLACKLISTED_KEYS_KEY] = emptyList<String>()
                properties[PLATFORM_VERSION_KEY] = 999
                config.update(properties)
            }
            service = IntegrationTestService(sandboxCreationService, connector, testDirectory)
        }
    }

    private val sandboxGroupsPerTest = mutableListOf<SandboxGroup>()
    private val cpis = mutableListOf<CPI>()

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
        sandboxGroupsPerTest.forEach(sandboxCreationService::unloadSandboxGroup)
        sandboxGroupsPerTest.clear()
        for (cpi in cpis) {
            cpi.close()
        }
        cpis.clear()
    }

    private fun loadAndInstantiate(resourceDigestCpi: String): SandboxGroup {
        //  "Install" -  the CPKs 'into the system', i.e. copy cpk to disk somewhere and scan the manifests.
        val cpi = service.loadCPIFromResource(resourceDigestCpi)
        val cpks = cpi.cpks
        cpis.add(cpi)

        //  Instantiate -  load the cpi (its cpks) into the process, and allow OSGi to wire up things.
        val sandboxGroup = service.createSandboxGroupFor(cpks.toSet())

        // "preExecute" or "postInstantiate" "configure" up various per-CPI services.
        service.registerCrypto(sandboxGroup)

        sandboxGroupsPerTest.add(sandboxGroup)

        return sandboxGroup
    }

    /**
     *************  Tests  **************8
     */
    @Test
    fun `end to end crypto test for first cordapp as two cpks`() {
        val sandboxGroup = loadAndInstantiate(Constants.DIGEST_CPB_ONE)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroup)
    }

    @Test
    fun `first consumer cannot use other CPI digest`() {
        val sandboxGroup = loadAndInstantiate(Constants.DIGEST_CPB_ONE)

        assertThrows<IllegalArgumentException> {
            service.runFlow<SecureHash>("com.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroup)
        }
    }

    @Test
    fun `end to end crypto test for second cordapp as two cpks`() {
        val sandboxGroup = loadAndInstantiate(Constants.DIGEST_CPB_TWO)
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroup)
    }

    @Test
    fun `second consumer cannot use other CPI digest`() {
        val sandboxGroup = loadAndInstantiate(Constants.DIGEST_CPB_TWO)

        assertThrows<IllegalArgumentException> {
            // NOTE the 'package' is >> org << not com for 'TWO'.
            service.runFlow<SecureHash>("org.example.CryptoConsumerTryAndUseOtherDigest", sandboxGroup)
        }
    }

    @Test
    fun `load two cpis side by side`() {
        val sandboxGroupOne = loadAndInstantiate(Constants.DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(Constants.DIGEST_CPB_TWO)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)
    }

    @Test
    fun `load two cpis side by side cannot use each others custom digests`() {
        val sandboxGroupOne = loadAndInstantiate(Constants.DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(Constants.DIGEST_CPB_TWO)

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
        val sandboxGroup = loadAndInstantiate(Constants.DIGEST_CPB_ONE)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroup)

        service.unloadSandboxGroup(sandboxGroup)

        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroup)
        }
    }

    @Test
    fun `load two cpis then unload both`() {
        val sandboxGroupOne = loadAndInstantiate(Constants.DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(Constants.DIGEST_CPB_TWO)

        // We can use the two different custom cryptos
        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)

        // explicitly unload sandboxGroupOne
        service.unloadSandboxGroup(sandboxGroupOne)

        // we can still run "two"
        service.runFlow<SecureHash>(DIGEST_TWO_CLASSNAME, sandboxGroupTwo)

        // but not sandboxGroupOne
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        }

        // explicitly unload sandboxGroupTwo
        service.unloadSandboxGroup(sandboxGroupTwo)

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
        val sandboxGroupOne = loadAndInstantiate(Constants.DIGEST_CPB_ONE)
        val sandboxGroupTwo = loadAndInstantiate(Constants.DIGEST_CPB_ONE)

        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupTwo)

        // explicitly unwire *and* unload the first sandbox group
        service.unloadSandboxGroup(sandboxGroupOne)

        // But we can still run the same identical custom crypto code in the second sandbox group
        service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupTwo)

        // But not in the first, because it's been removed.
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupOne)
        }

        // Explicitly unwire *and* unload the second sandbox group
        service.unloadSandboxGroup(sandboxGroupTwo)

        // And now we cannot run anything in the second group.
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(DIGEST_ONE_CLASSNAME, sandboxGroupTwo)
        }
    }
}
