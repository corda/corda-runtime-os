package net.corda.processors.crypto.tests

import com.typesafe.config.ConfigFactory
import net.corda.crypto.CryptoConsts
import net.corda.crypto.CryptoOpsClient
import net.corda.crypto.service.CryptoOpsService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.lang.Thread.sleep
import java.util.UUID


@ExtendWith(ServiceExtension::class)
class CryptoOpsTests {
    companion object {
        private val logger = contextLogger()

        private val CLIENT_ID = "${CryptoOpsTests::class.java}-integration-test"

        private const val CRYPTO_CONFIGURATION: String = "{}"

        private const val BOOT_CONFIGURATION = """
        instanceId=1
    """

        private fun Lifecycle.stopAndWait() {
            stop()
            isStopped()
        }

        private fun Lifecycle.startAndWait() {
            start()
            isStarted()
        }

        private fun Lifecycle.isStopped() = eventually {
            assertFalse(isRunning, "Failed waiting to stop for ${this::class.java.name}")
        }

        private fun Lifecycle.isStarted() = eventually {
            assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
        }
    }

    @InjectService(timeout = 5000L)
    lateinit var coordinatorFactory: LifecycleCoordinatorFactory

    @InjectService(timeout = 5000L)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 5000L)
    lateinit var processor: CryptoProcessor

    @InjectService(timeout = 5000L)
    lateinit var client: CryptoOpsClient

    lateinit var testCoordinator: LifecycleCoordinator

    lateinit var testRegistrationHandle: RegistrationHandle

    lateinit var tenantId: String

    var up = false

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()

        // Publish crypto config
        with(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))) {
            publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.CRYPTO_CONFIG,
                        Configuration(CRYPTO_CONFIGURATION, "1")
                    )
                )
            )
        }

        val bootstrapConfig = SmartConfigFactory.create(
            ConfigFactory.empty()).create(ConfigFactory.parseString(BOOT_CONFIGURATION)
        )

        processor.start(bootstrapConfig)

        testCoordinator = coordinatorFactory.createCoordinator<CryptoOpsTests> { event, _ ->
            logger.info("Received event $event")
            if(event is RegistrationStatusChangeEvent && event.status == LifecycleStatus.UP) {
                logger.info("All required dependencies are up...")
                up = true
            }
        }
        testCoordinator.postEvent(StartEvent())
        testRegistrationHandle = testCoordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                LifecycleCoordinatorName.forComponent<CryptoOpsService>()
            )
        )
        logger.info("Registered to follow $testRegistrationHandle")

        client.startAndWait()
        eventually {
            assertTrue(up)
        }
    }

    @Test
    fun `Should be able to use crypto operations`() {
        val supportedSchemes = client.getSupportedSchemes(tenantId, CryptoConsts.CryptoCategories.LEDGER)
        assertTrue(supportedSchemes.isNotEmpty())
    }
}