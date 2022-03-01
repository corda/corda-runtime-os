package net.corda.install.local.file.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.install.InstallService
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit


@ExtendWith(value = [ServiceExtension::class, BundleContextExtension::class])
class LocalPackageCacheIntegrationTest {

    @InjectService(timeout = 1000)
    lateinit var installService: InstallService

    @InjectService(timeout = 1000)
    lateinit var configReadService : ConfigurationReadService

    @InjectService(timeout = 1000)
    lateinit var publisherFactory: PublisherFactory

    lateinit var testDir : Path
    lateinit var cpiDir : Path

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
        val config = ConfigFactory.parseMap(mapOf("cacheDir" to cpiDir.toString())).root().render(ConfigRenderOptions.concise())
        val avroConfig = Configuration(config, "1")
        val publisher = publisherFactory.createPublisher(PublisherConfig("foo"))
        publisher.publish(listOf(Record(CONFIG_TOPIC, "corda.cpi", avroConfig)))
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
        // Disabled until local package cache is re-factored for CPK distribution
//        installService.registerForUpdates { cpiIds : NavigableSet<CPI.Identifier>, _ ->
//            for(cpiId in cpiIds) {
//                val cpi = installService.get(cpiId).get()
//                Assertions.assertNotNull(cpi)
//                Assertions.assertEquals(cpiId, cpi?.metadata?.id)
//            }
//            synchronized(lock) {
//                lock.notify()
//            }
//        }
//        configReadService.bootstrapConfig(
//            SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty()))
//        synchronized(lock) {
//            lock.wait()
//        }
    }
}
