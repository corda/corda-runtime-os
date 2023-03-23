package net.corda.interop.service.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.data.interop.InteropMessage
import net.corda.data.p2p.app.*
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
import net.corda.schema.Schemas.Config.CONFIG_TOPIC
import net.corda.schema.Schemas.P2P.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
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
// ./gradlew clean :components:interop:interop-service:integrationTest
// ./gradlew clean :components:interop:interop-service:testOSGi
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
    fun `verify messages from p2p-in are send back to p2p-out`() {
        val aliceX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
        val interopMessage : ByteBuffer = ByteBuffer.wrap(cordaAvroSerializationFactory.createAvroSerializer<InteropMessage> { }.serialize(
            InteropMessage("InteropMessageID-01",
                """
                    {
                      "method": "org.corda.interop/platform/tokens/v1.0/reserve-token",
                      "parameters": [
                        {
                          "abc": {
                            "type": "string",
                            "value": "USD"
                          }
                        }
                      ]
                    }
                """.trimIndent())))

        val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }
        val sourceIdentity = HoldingIdentity("CN=Alice Alias, O=Alice Corp, L=LDN, C=GB", groupId)
        val destinationIdentity = HoldingIdentity("CN=Alice Alias Alter Ego, O=Alice Alter Ego Corp, L=LDN, C=GB", groupId)
        val identity = HoldingIdentity(aliceX500Name, groupId)
        val header = UnauthenticatedMessageHeader(destinationIdentity, sourceIdentity, "interop" , "1")
        val sessionEvent = SessionEvent(
            MessageDirection.INBOUND, Instant.now(), aliceX500Name, 1, identity, identity, 0, listOf(), SessionInit(
                aliceX500Name,
                null,
                emptyKeyValuePairList(),
                emptyKeyValuePairList(),
                emptyKeyValuePairList(),
                ByteBuffer.wrap("".toByteArray())
            )
        )
        val interopRecord = Record(P2P_IN_TOPIC, aliceX500Name, AppMessage(UnauthenticatedMessage(header, interopMessage)))
        val nonInteropFlowHeader = AuthenticatedMessageHeader(identity, identity, Instant.ofEpochMilli(1),
            "", "", "flowSession", MembershipStatusFilter.ACTIVE)
        val nonInteropSessionRecord = Record(
            P2P_IN_TOPIC, aliceX500Name, AppMessage(
                AuthenticatedMessage(
                    nonInteropFlowHeader, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))
                )
            )
        )

        interopService.start()
        val publisher = publisherFactory.createPublisher(PublisherConfig(aliceX500Name), bootConfig)
        // Test config updates don't break Interop Service
        republishConfig(publisher)
        publisher.publish(listOf(interopRecord, interopRecord, nonInteropSessionRecord))

        val flowMapperExpectedOutputMessages = 2
        flowMapperExpectedOutputMessages.let { expectedMessageCount ->
            val mapperLatch = CountDownLatch(expectedMessageCount)
            val testProcessor = P2POutMessageCounter(aliceX500Name, mapperLatch, expectedMessageCount)
            val eventTopic = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("$aliceX500Name-p2p-out", P2P_OUT_TOPIC),
                testProcessor,
                bootConfig,
                null
            )
            eventTopic.start()
            assertTrue(
                mapperLatch.await(30, TimeUnit.SECONDS),
                "Fewer P2P output messages were observed (${testProcessor.recordCount}) than expected ($expectedMessageCount)."
            )
            assertEquals(
                expectedMessageCount,
                testProcessor.recordCount,
                "More P2P output messages were observed that expected."
            )
            eventTopic.close()
        }
        interopService.stop()
    }

    private fun setupConfig(publisher: Publisher) {
        publishConfig(publisher)
        configService.start()
        configService.bootstrapConfig(bootConfig)
        membershipGroupReaderProvider.start()
    }

    private fun publishConfig(publisher: Publisher) {
        val messagingConf = """
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
        publisher.publish(listOf(Record(CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, messagingConf, 0, schemaVersion))))
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
}

class P2POutMessageCounter(
    private val key: String,
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int
) : DurableProcessor<String, AppMessage> {
    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
    var recordCount = 0
    override fun onNext(events: List<Record<String, AppMessage>>): List<Record<*, *>> {
        for (event in events) {
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
