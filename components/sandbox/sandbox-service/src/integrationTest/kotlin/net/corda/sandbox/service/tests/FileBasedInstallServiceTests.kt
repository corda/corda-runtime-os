package net.corda.sandbox.service.tests

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.configuration.read.ConfigurationReadService
import net.corda.install.InstallService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.service.SandboxService
import net.corda.sandbox.service.tests.Constants.CPB_ONE
import net.corda.sandbox.service.tests.Constants.CPB_THREE
import net.corda.sandbox.service.tests.Constants.CPB_TWO
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.getUniqueObject
import net.corda.sandboxgroupcontext.putUniqueObject
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * These tests probably need deleting when we switch to a different implementation
 * of the cpk service.
 *
 * These tests use the file-backed [InstallService]
 */
@ExtendWith(ServiceExtension::class)
class FileBasedInstallServiceTests {
    companion object {
        const val FLOW_ONE = "com.example.HelloWorldFlowOne"
        const val FLOW_TWO = "org.example.HelloWorldFlowTwo"
        const val FLOW_THREE = "com.example.HelloWorldFlowThree"

        @InjectService
        lateinit var configAdmin: ConfigurationAdmin

        @InjectService
        lateinit var sandboxCreationService: SandboxCreationService

        @InjectService
        lateinit var installService: InstallService

        @InjectService
        lateinit var sandboxService: SandboxService

        lateinit var service: IntegrationTestService

        @InjectService
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService
        lateinit var smartConfigFactory: SmartConfigFactory

        @JvmStatic
        @BeforeAll
        fun setup(@TempDir testDirectory: Path) {
            val cacheDir = Files.createTempDirectory(testDirectory, "installServiceCacheDir")
            val properties = Hashtable<String, Any>()
            properties[Constants.BASE_DIRECTORY_KEY] = testDirectory.toString()
            properties[Constants.BLACKLISTED_KEYS_KEY] = emptyList<String>()
            properties[Constants.PLATFORM_VERSION_KEY] = 999
            configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
                config.update(properties)
            }

            val configFile = Files.createTempFile(testDirectory, "testConfiguration", ".json")
            Files.newBufferedWriter(configFile).use {
                it.write(
                    ConfigFactory.parseMap(
                        mapOf("corda" to mapOf("cpi" to mapOf("cacheDir" to cacheDir.toString())))
                    ).root().render(ConfigRenderOptions.concise())
                )
            }

            service = IntegrationTestService(sandboxCreationService, testDirectory)

            // The file-backed install service looks in a particular folder when it starts
            // so we need to make sure the files are there.
            service.copyCPIFromResource(CPB_ONE, cacheDir)
            service.copyCPIFromResource(CPB_TWO, cacheDir)
            service.copyCPIFromResource(CPB_THREE, cacheDir)

            // We need to tell the file-backed install service via the config
            // service where to find the above folder.  Unfortunately we seem to have to
            // do this via an intermediate file in the 'bootstrap config' rather than in the
            // config itself.
            val cfg = ConfigFactory.parseMap(mapOf("config.file" to configFile.toAbsolutePath().toString()))

            configurationReadService.start()

            var ready = false

            installService.registerForUpdates { _, _ -> ready = true }
            installService.start()

            configurationReadService.bootstrapConfig(smartConfigFactory.create(cfg))

            // wait until install service is 'ready' and has called us back with the
            // 'installed' cpi/cpbs.
            while (!ready) {
                Thread.sleep(100)
            }

            sandboxService.start()
        }

        @AfterAll
        fun teardown() {
            sandboxService.stop()
        }
    }

    private fun useSandboxService(resourceDigestCpi: String): SandboxGroupContext {
        val cpiIdentifier = service.getCpiIdentifier(resourceDigestCpi)

        val sandboxGroupContext = sandboxService.getOrCreateByCpiIdentifier(
            HoldingIdentity("OU=MegaBank", "1234"),
            cpiIdentifier, SandboxGroupType.FLOW
        ) { _, ctx ->
            ctx.putUniqueObject(resourceDigestCpi) // just put the name in, once, as a string.
            AutoCloseable { }
        }

        return sandboxGroupContext
    }

    /** Would be nicer to split this up into smaller tests, but shows end-to-end
     * behaviour for now.
     */
    @Test
    fun `run flows using sandboxgroupcontext and install service`() {
        // Run flow 1 in cpb 1
        val sandboxGroupCtx1 = useSandboxService(CPB_ONE)
        assertThat(sandboxGroupCtx1.getUniqueObject<String>()).isEqualTo(CPB_ONE)
        service.runFlow<SecureHash>(FLOW_ONE, sandboxGroupCtx1.sandboxGroup)
        assertThat(sandboxGroupCtx1.getUniqueObject<String>()).isEqualTo(CPB_ONE)

        // Run flow 2 in cpb2
        val sandboxGroupCtx2 = useSandboxService(CPB_TWO)
        assertThat(sandboxGroupCtx2.getUniqueObject<String>()).isEqualTo(CPB_TWO)
        service.runFlow<SecureHash>(FLOW_TWO, sandboxGroupCtx2.sandboxGroup)
        assertThat(sandboxGroupCtx2.getUniqueObject<String>()).isEqualTo(CPB_TWO)

        // Can run flow only in CPB 3
        val sandboxGroupCtx3 = useSandboxService(CPB_THREE)
        assertThat(sandboxGroupCtx3.getUniqueObject<String>()).isEqualTo(CPB_THREE)
        service.runFlow<SecureHash>(FLOW_ONE, sandboxGroupCtx3.sandboxGroup)
        service.runFlow<SecureHash>(FLOW_TWO, sandboxGroupCtx3.sandboxGroup)
        service.runFlow<SecureHash>(FLOW_THREE, sandboxGroupCtx3.sandboxGroup)
        assertThat(sandboxGroupCtx3.getUniqueObject<String>()).isEqualTo(CPB_THREE)

        // Cannot run flow 2, in sandbox 1.
        assertThrows<SandboxException> {
            service.runFlow<SecureHash>(FLOW_TWO, sandboxGroupCtx1.sandboxGroup)
        }
    }
}