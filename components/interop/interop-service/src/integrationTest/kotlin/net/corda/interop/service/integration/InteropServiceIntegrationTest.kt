package net.corda.interop.service.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.interop.InteropService
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// To run the test:
// ./gradlew :components:interop:interop-service:integrationTest
@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InteropServiceIntegrationTest {

    private companion object {
        const val clientId = "clientId"
    }

    private var setup = false

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var configService: ConfigurationReadService

    @InjectService(timeout = 4000)
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

    @InjectService(timeout = 4000)
    lateinit var interopService: InteropService

    private val bootConfig = SmartConfigImpl.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("INMEMORY"))
        .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))

    private val schemaVersion = ConfigurationSchemaVersion(1, 0)

    @BeforeEach
    fun setup() {
        if (!setup) {
            setup = true
            val publisher = publisherFactory.createPublisher(PublisherConfig(clientId), bootConfig)
            setupConfig(publisher)
        }
    }

    @Test
    fun `verify messages from p2p-in are send back to p2p-out`() {
        interopService.start()
        val testId = "test1"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), bootConfig)

        val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }
        val flowEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<FlowEvent> { }

        // Test config updates don't break Interop Service
        republishConfig(publisher)

        val identity = HoldingIdentity(testId, testId)
        val flowHeader = AuthenticatedMessageHeader(identity, identity, Instant.ofEpochMilli(1), "", "", "interop")
        val version = listOf(1)
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND, Instant.now(), testId, 1, identity, identity, 0, listOf(), SessionInit(
                testId,
                version,
                testId,
                null,
                KeyValuePairList(emptyList()),
                KeyValuePairList(emptyList()),
                ByteBuffer.wrap("".toByteArray())
            )
        )

        val sessionRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(
                AuthenticatedMessage(
                    flowHeader, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))
                )
            )
        )

        val invalidHeader = AuthenticatedMessageHeader(identity, identity, Instant.ofEpochMilli(1), "", "", "other")
        val invalidEvent = FlowEvent(testId, sessionEvent)
        val invalidRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(
                AuthenticatedMessage(
                    invalidHeader, ByteBuffer.wrap(flowEventSerializer.serialize(invalidEvent))
                )
            )
        )

        val nonInteropFlowHeader = AuthenticatedMessageHeader(identity, identity, Instant.ofEpochMilli(1), "", "", "flowSession")
        val nonInteropSessionRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(
                AuthenticatedMessage(
                    nonInteropFlowHeader, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))
                )
            )
        )

        publisher.publish(listOf(sessionRecord, sessionRecord, nonInteropSessionRecord, invalidRecord))

        //validate mapper receives 2 inits
        val mapperLatch = CountDownLatch(2)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-p2p-out", P2P_OUT_TOPIC),
            P2POutMessageCounter("$testId", mapperLatch, 2),
            bootConfig,
            null
        )
        p2pOutSub.start()
        assertTrue(mapperLatch.await(30, TimeUnit.SECONDS))
        p2pOutSub.close()

        interopService.stop()
    }

    private fun setupConfig(publisher: Publisher) {
        publishConfig(publisher)
        configService.start()
        configService.bootstrapConfig(bootConfig)
    }

    private fun publishConfig(publisher: Publisher) {
        publisher.publish(
            listOf(
                Record(
                    CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, messagingConf, 0, schemaVersion)
                )
            )
        )
    }

    private fun republishConfig(publisher: Publisher) {
        // Wait for the initial config to be available
        val configLatch = CountDownLatch(1)
        configService.registerForUpdates { _, _ ->
            configLatch.countDown()
        }
        configLatch.await()

        publishConfig(publisher)
    }


    private val messagingConf = """
            componentVersion="5.1"
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
            }
      """
}


class P2POutMessageCounter(
    private val key: String,
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int
) : DurableProcessor<String, AuthenticatedMessage> {

    override val keyClass = String::class.java
    override val valueClass = AuthenticatedMessage::class.java

    private var recordCount = 0

    override fun onNext(events: List<Record<String, AuthenticatedMessage>>): List<Record<*, *>> {
        for (event in events) {
            File(event.toString()).printWriter().use { out -> out.println(key) }
            if (event.key == key) {
                recordCount++
                if (recordCount > expectedRecordCount) {
                    fail("Expected record count exceeded in events processed for this key")
                }
                latch.countDown()
            }
        }

        return emptyList()
    }
}