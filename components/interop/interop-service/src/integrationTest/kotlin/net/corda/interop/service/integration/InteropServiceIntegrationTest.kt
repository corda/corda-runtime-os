package net.corda.interop.service.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.data.interop.InteropMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.interop.InteropService
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Config.CONFIG_TOPIC
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// To run the test outside Intellij:
// ./gradlew clean :components:interop:interop-service:integrationTest --tests 'verify messages from flow interop event are send to flow mapper event'
// ./gradlew clean :components:interop:interop-service:testOSGi --tests 'verify messages from flow interop event are send to flow mapper event'
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
    lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

    @InjectService(timeout = 4000)
    lateinit var interopService: InteropService

    private val bootConfig = SmartConfigImpl.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("INMEMORY"))
        .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
        .withValue(BOOT_MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(100000000))

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
    fun `verify messages from flow interop event are send to flow mapper event`() {
        interopService.start()
        val key = "test1"
        val aliceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val aliceGroupId = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
        val payload = "{\"method\": \"org.corda.interop/platform/tokens/v1.0/reserve-token\", \"parameters\" : [ { \"abc\" : { \"type\" : \"string\", \"value\" : \"USD\" } } ] }"
        val publisher = publisherFactory.createPublisher(PublisherConfig(aliceX500Name), bootConfig)
        val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }
        val interopMessageSerializer = cordaAvroSerializationFactory.createAvroSerializer<InteropMessage> { }

        // Test config updates don't break Interop Service
        republishConfig(publisher)
        val sourceIdentity = HoldingIdentity("CN=Alice Alias, O=Alice Corp, L=LDN, C=GB", "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08")
        val destinationIdentity = HoldingIdentity("CN=Alice Alias Alter Ego, O=Alice Alter Ego Corp, L=LDN, C=GB", "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08")
        val identity = HoldingIdentity(aliceX500Name, aliceGroupId)
        val header = AuthenticatedMessageHeader(destinationIdentity, sourceIdentity, Instant.now(),
            "interop" , "1", "1", MembershipStatusFilter.ACTIVE)
        val sessionEvent = SessionEvent(
            MessageDirection.INBOUND, Instant.now(), aliceX500Name, 1, identity, identity, 0, listOf(), SessionInit(
                aliceX500Name,
                null,
                emptyKeyValuePairList(),
                emptyKeyValuePairList(),
                emptyKeyValuePairList(),
                ByteBuffer.wrap(interopMessageSerializer.serialize(InteropMessage("InteropMessageID-01", payload)))
            )
        )

        val interopRecord = Record(
            Schemas.Flow.FLOW_INTEROP_EVENT_TOPIC, key, AppMessage(
                AuthenticatedMessage(header, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent)))
            )
        )

        val nonInteropFlowHeader = AuthenticatedMessageHeader(identity, identity, Instant.ofEpochMilli(1), "", "", "flowSession", MembershipStatusFilter.ACTIVE)
        val nonInteropSessionRecord = Record(
            Schemas.Flow.FLOW_INTEROP_EVENT_TOPIC, aliceX500Name, AppMessage(
                AuthenticatedMessage(
                    nonInteropFlowHeader, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))
                )
            )
        )

        publisher.publish(listOf(interopRecord, interopRecord, nonInteropSessionRecord))

        val expectedOutputMessages = 2
        val mapperLatch = CountDownLatch(expectedOutputMessages)
        val testProcessor = FlowMapperEventCounter(key, mapperLatch, expectedOutputMessages)
        val flowMapperEventTopic = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$aliceX500Name-flow-mapper", Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC),
            testProcessor,
            bootConfig,
            null
        )
        flowMapperEventTopic.start()
        assertTrue(mapperLatch.await(30, TimeUnit.SECONDS),
            "Fewer output messages were observed (${testProcessor.recordCount}) than expected ($expectedOutputMessages).")
        assertEquals(expectedOutputMessages, testProcessor.recordCount, "More output messages were observed that expected.")
        flowMapperEventTopic.close()

        interopService.stop()
    }

    private fun setupConfig(publisher: Publisher) {
        publishConfig(publisher)
        configService.start()
        configService.bootstrapConfig(bootConfig)
        membershipGroupReaderProvider.start()
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

class FlowMapperEventCounter(
    private val key: String,
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int
) : DurableProcessor<String, FlowMapperEvent> {
    override val keyClass = String::class.java
    override val valueClass = FlowMapperEvent::class.java
    var recordCount = 0
    override fun onNext(events: List<Record<String, FlowMapperEvent>>): List<Record<*, *>> {
        for (event in events) {
            recordCount++
            if (recordCount > expectedRecordCount) {
                fail("Expected record count exceeded in events processed for this key")
            }
            latch.countDown()

        }
        return emptyList()
    }
}
