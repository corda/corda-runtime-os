package net.corda.install.local.file.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.configuration.read.ConfigurationReadService
import net.corda.install.InstallService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.packaging.CPI
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.NavigableSet
import java.util.concurrent.TimeUnit


@ExtendWith(value = [ServiceExtension::class, BundleContextExtension::class])
class LocalPackageCacheIntegrationTest {

    @InjectService(timeout = 1000)
    lateinit var installService: InstallService

    @InjectService(timeout = 1000)
    lateinit var configReadService : ConfigurationReadService

    lateinit var testDir : Path
    lateinit var cpiDir : Path
    lateinit var configFile : Path

    private val lock = Object()

    @BeforeEach
    fun setup(@TempDir testDir : Path) {
        this.testDir = testDir
        cpiDir = testDir.resolve("cpi").also(Files::createDirectory)
        System.getProperty("cpi.path").split(System.getProperty("path.separator")).asSequence()
            .map(Path::of)
            .forEach {
                Files.copy(it, cpiDir.resolve(it.fileName))
            }
        configFile = Files.createTempFile(testDir, "testConfiguration", ".json")
        Files.newBufferedWriter(configFile).use {
            it.write(ConfigFactory.parseMap(mapOf("corda" to
                    mapOf("cpi" to
                            mapOf("cacheDir" to cpiDir.toString())
                    )
            )).root().render(ConfigRenderOptions.concise()))
        }
        configReadService.start()
        installService.start()
    }

    @AfterEach
    fun afterEach() {
        installService.stop()
        configReadService.stop()
    }


    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `check all advertised CPIs are available`() {
        installService.registerForUpdates { cpiIds : NavigableSet<CPI.Identifier>, _ ->
            for(cpiId in cpiIds) {
                val cpi = installService.get(cpiId).get()
                Assertions.assertNotNull(cpi)
                Assertions.assertEquals(cpiId, cpi?.metadata?.id)
            }
            synchronized(lock) {
                lock.notify()
            }
        }
        configReadService.bootstrapConfig(
            SmartConfigFactory.create(ConfigFactory.empty()).create(
                ConfigFactory.parseMap(mapOf("config.file" to configFile.toAbsolutePath().toString()))))
        synchronized(lock) {
            lock.wait()
        }
    }
}