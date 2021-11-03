package net.corda.cpi.integration.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.utils.CPX_KAFKA_FILE_CACHE_ROOT_DIR_CONFIG_PATH
import net.corda.cpi.write.factory.CPIWriteFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.packaging.CPI
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.Logger
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch

@ExtendWith(ServiceExtension::class)
class CPIRPCIntegrationTest {

    private lateinit var rpcConfig: RPCConfig<String, String>
    private lateinit var kafkaConfig: Config
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        val logger: Logger = contextLogger()
        const val CLIENT_ID = "integrationTestRPCSender"

        @InjectService(timeout = 4000)
        lateinit var topicAdmin: KafkaTopicAdmin

        private val kafkaProperties = getKafkaProperties()

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC_TEMPLATE)
            topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC_TEMPLATE)
        }
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000, filter = "(type=kafka)")
    lateinit var readFactory: CPIReadFactory

    @InjectService(timeout = 4000, filter = "(type=kafka)")
    lateinit var writeFactory: CPIWriteFactory

    @TempDir
    lateinit var cacheDir: Path


    @BeforeEach
    fun beforeEach() {
        kafkaConfig = ConfigFactory.empty()
            .withValue(
                IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(
                    IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE
                )
            )
            .withValue(IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
            .withValue(
                CPX_KAFKA_FILE_CACHE_ROOT_DIR_CONFIG_PATH,
                ConfigValueFactory.fromAnyRef(cacheDir.toString())
            )
    }

    @Test
    fun `subscribe to receive CPI identity objects`() {

        val initlatch = CountDownLatch(1)
        val endlatch = CountDownLatch(2)
        var listeners = mutableMapOf<CPI.Identifier, CPI.Metadata>()

        val cpiPath = System.getProperty("cpi.path")
        val rootPath = Paths.get(cpiPath).parent

        kafkaConfig = kafkaConfig.withValue("CPIDirectory", ConfigValueFactory.fromAnyRef(rootPath.toString()))
        rpcConfig = RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, String::class.java, String::class.java)

        val cpiReader = readFactory.createCPIRead(kafkaConfig)
        val cpiWriter = writeFactory.createCPIWrite(kafkaConfig)

        cpiReader.start()

        cpiReader.registerCallback { _, currentSnapshot ->
                listeners.putAll(currentSnapshot)
                initlatch.countDown()
                endlatch.countDown()
                if (currentSnapshot.size == 1) {
                    endlatch.countDown()
                }
        }

        initlatch.await()
        cpiWriter.start()
        endlatch.await()

        assertEquals( 1, listeners.keys.size)
        val cpiMetadata = listeners.values.first()
        assertEquals(2, cpiMetadata.cpks.size)
        val cpksFound = cpiMetadata.cpks.map { cpk -> cpk.id.name }.toSet()
        assertEquals(setOf("net.corda.contract-cpk", "net.corda.workflow-cpk"), cpksFound)

        cpiReader.stop()
        cpiWriter.stop()
    }

    @Test
    fun `CPI RPC Test`() {

        val initlatch = CountDownLatch(1)
        val endlatch = CountDownLatch(2)
        var cpis: Map<CPI.Identifier, CPI.Metadata> = emptyMap()

        val cpiPath = System.getProperty("cpi.path")
        val rootPath = Paths.get(cpiPath).parent

        kafkaConfig = kafkaConfig.withValue("CPIDirectory", ConfigValueFactory.fromAnyRef(rootPath.toString()))
        rpcConfig = RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, String::class.java, String::class.java)

        val cpiReader = readFactory.createCPIRead(kafkaConfig)
        val cpiWriter = writeFactory.createCPIWrite(kafkaConfig)
        cpiReader.start()

        cpiReader.registerCallback { _, currentSnapshot ->
            initlatch.countDown()
            endlatch.countDown()
            if (currentSnapshot.size == 1) {
                cpis = currentSnapshot.toMutableMap()
                endlatch.countDown()
            }
        }

        initlatch.await()
        cpiWriter.start()
        endlatch.await()

        Assertions.assertThat(cpis.keys.size == 1)
        logger.info("CPIs received: ${cpis.toString()}")

        // Now have a CPI, try to stream it across
        val cpiIdentifier = cpis.keys.first()
        val ret = cpiReader.getCPI(cpiIdentifier)
        val inStream = ret.get()

        // now check that the CPI we streamed across is equal to the CPI we started with.
        Files.newInputStream(Paths.get(cpiPath)).use { originalStream ->
            inStream.use { copiedStream ->
                assertTrue(contentsAreSame(originalStream, copiedStream))
            }
        }
        cpiReader.stop()
        cpiWriter.stop()
    }

    private fun contentsAreSame(in1: InputStream, in2: InputStream): Boolean {
        while (true) {
            val data1 = in1.read()
            val data2 = in2.read()
            if (data1 != data2) {
                return false
            }
            if (data1 == -1) {
                return true
            }
        }
    }
}