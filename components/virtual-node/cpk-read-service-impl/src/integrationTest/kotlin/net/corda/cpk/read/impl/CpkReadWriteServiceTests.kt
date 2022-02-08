package net.corda.cpk.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.read.CpkReadService
import net.corda.cpk.read.impl.testing.WaitingComponent
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.cpk.readwrite.resolvePath
import net.corda.cpk.write.CpkWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.packaging.CPK
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(value = [ServiceExtension::class, BundleContextExtension::class])
class CpkReadWriteServiceTests {
    @InjectService(timeout = 1000)
    lateinit var reader: CpkReadService

    @InjectService(timeout = 1000)
    lateinit var writer: CpkWriteService

    @InjectService(timeout = 1000)
    lateinit var configReadService: ConfigurationReadService

    @InjectService(timeout = 1000)
    lateinit var coordinatorFactory: LifecycleCoordinatorFactory

    lateinit var waiter: WaitingComponent

    lateinit var testDir: Path
    lateinit var perProcessCpkCacheDir: Path  // this is 'internal' to the CPK loader code.
    lateinit var commonCpkDir: Path
    lateinit var cpkOnePath: Path

    /** Return the meta data for a cpk at a given path */
    private fun getCpkMetaDataFromPath(cpkPath: Path): CPK.Metadata = Files.newInputStream(cpkPath).use {
        CPK.from(it, perProcessCpkCacheDir, null, true).use {
            it.metadata
        }
    }

    /** Simple bootstrap config that *should* match what we need for this service to run */
    private fun getBootstrapConfig(): SmartConfig {

        // has "no secrets"
        val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

        return smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    CpkServiceConfigKeys.CPK_CACHE_DIR to commonCpkDir.toString()
                )
            )
        )
    }

    @BeforeEach
    private fun beforeEach(@TempDir perTestDir: Path) {
        testDir = perTestDir
        perProcessCpkCacheDir = testDir.resolve("cpi").also(Files::createDirectory)
        commonCpkDir = testDir.resolve("common-folder-we-pretend-is-kafka").also(Files::createDirectory)

        // We're passing in the CPK via
        // * build.gradle (which sets the command line param)
        // * tests.bndrun (which passes the command line param)
        cpkOnePath = System.getProperty("cpk.one.path").let(Path::of)

        // Create a component that waits for the UP status of other components.
        waiter = WaitingComponent(coordinatorFactory)
        val latch = CountDownLatch(1)
        waiter.onUp { latch.countDown() }
        waiter.onWait { latch.await(3, TimeUnit.SECONDS) }
        waiter.waitFor(LifecycleCoordinatorName.forComponent<CpkWriteService>())
        waiter.waitFor(LifecycleCoordinatorName.forComponent<CpkReadService>())
        waiter.start()

        reader.start()
        writer.start()
        configReadService.start()
    }

    @AfterEach
    private fun afterEach() {
        reader.stop()
        writer.stop()
        configReadService.stop()
        waiter.stop()
    }

    @Test
    fun `read after write`() {
        configReadService.bootstrapConfig(getBootstrapConfig())

        // Waits for our components to be 'up'
        waiter.waitForLifecycleStatusUp()

        val metadata = getCpkMetaDataFromPath(cpkOnePath)

        // Check that the cpk hasn't been written yet.
        val cpkBefore = reader.get(metadata)
        assertThat(cpkBefore).isNull()

        // Test write
        Files.newInputStream(cpkOnePath).use {
            writer.put(metadata, it)
        }
        assertThat(Files.exists(metadata.resolvePath(commonCpkDir))).isTrue

        // Test read
        val cpk = reader.get(metadata)!!
        assertThat(cpk.metadata).isEqualTo(metadata)
        cpk.close()
    }
}
