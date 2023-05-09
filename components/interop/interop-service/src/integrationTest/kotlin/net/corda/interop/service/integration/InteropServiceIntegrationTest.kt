package net.corda.interop.service.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.data.interop.InteropMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.interop.InteropAliasProcessor.Companion.createHostedAliasIdentity
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
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.virtualnode.toCorda
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
        .withValue(MessagingConfig.MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(100000000))

    private val schemaVersion = ConfigurationSchemaVersion(1, 0)

    @BeforeEach
    fun setup() {
        if (!setup) {
            setup = true
            val publisher = publisherFactory.createPublisher(PublisherConfig(clientId), bootConfig)
            setupConfig(publisher)
        }
    }

    val groupId = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
    val sourceIdentity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", groupId)
    val destinationIdentity = HoldingIdentity("CN=Alice Alias, O=Alice Corp, L=LDN, C=GB", groupId)

    private fun messagesToPublish(session: String) : List<Record<*,*>> {
        val interopMessage: ByteBuffer = ByteBuffer.wrap(
            cordaAvroSerializationFactory.createAvroSerializer<InteropMessage> { }.serialize(
                InteropMessage(
                    "InteropMessageID-01",
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
                """.trimIndent()
                )
            )
        )

        val contextUserProperties = KeyValuePairList(listOf(
            KeyValuePair("INTEROP_FACADE_ID", "org.corda.interop/platform/hello-interop/v1.0"),
            KeyValuePair("INTEROP_FACADE_METHOD", "say-hello"),
            KeyValuePair("INTEROP_GROUP_ID", "fake_group_id")
        ))

        val inboundSessionEvent = SessionEvent(
            MessageDirection.INBOUND, Instant.now(), session, 1, destinationIdentity, sourceIdentity, 0, listOf(),
            SessionInit(sourceIdentity.toString(), null, contextUserProperties, emptyKeyValuePairList(), emptyKeyValuePairList(), interopMessage))

        val outboundSessionEvent = SessionEvent(
            MessageDirection.OUTBOUND, Instant.now(), session, 2, destinationIdentity, sourceIdentity, 0, listOf(),
            SessionInit(sourceIdentity.toString(), null, contextUserProperties, emptyKeyValuePairList(), emptyKeyValuePairList(), interopMessage))

        val inboundMsg = Record(Schemas.Flow.FLOW_INTEROP_EVENT_TOPIC, session, FlowMapperEvent(inboundSessionEvent))
        val outboundMsg = Record(Schemas.Flow.FLOW_INTEROP_EVENT_TOPIC, session, FlowMapperEvent(outboundSessionEvent))
        return listOf(inboundMsg, outboundMsg)
    }

    @Test
    fun `verify interop processor sends messages to flow mapper event topic and p2p out topic`() {
        interopService.start()
        val publisher = publisherFactory.createPublisher(PublisherConfig("client1"), bootConfig)
        // Test config updates don't break Interop Service
        republishConfig(publisher)
        publisher.publish(listOf(createHostedAliasIdentity(sourceIdentity.toCorda())))
        val session = "session1"
        //TODO revisit sleep in CORE-12134
        Thread.sleep(30000)
        publisher.publish(messagesToPublish(session))

        val flowMapperExpectedOutputMessages = 1
        flowMapperExpectedOutputMessages.let { expectedMessageCount ->
            val mapperLatch = CountDownLatch(expectedMessageCount)
            val testProcessor = FlowMapperEventCounter(session, mapperLatch, expectedMessageCount)
            val eventTopic = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("client-flow-mapper", FLOW_MAPPER_EVENT_TOPIC),
                testProcessor,
                bootConfig,
                null
            )
            eventTopic.start()
            assertTrue(
                mapperLatch.await(60, TimeUnit.SECONDS),
                "Fewer messages on $FLOW_MAPPER_EVENT_TOPIC were observed (${testProcessor.recordCount})" +
                        " than expected ($expectedMessageCount)."
            )
            assertEquals(expectedMessageCount, testProcessor.recordCount,
                "More messages on $FLOW_MAPPER_EVENT_TOPIC were observed that expected.")
            eventTopic.close()
        }
        val p2pExpectedOutputMessages = 1
        p2pExpectedOutputMessages.let { expectedMessageCount ->
            val mapperLatch = CountDownLatch(expectedMessageCount)
            val testProcessor = P2POutMessageCounter(session, mapperLatch, expectedMessageCount)
            val eventTopic = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("client-p2p-out", P2P_OUT_TOPIC),
                testProcessor,
                bootConfig,
                null
            )
            eventTopic.start()
            assertTrue(
                mapperLatch.await(45, TimeUnit.SECONDS),
                "Fewer messages on $P2P_OUT_TOPIC were observed (${testProcessor.recordCount})" +
                        " than expected ($expectedMessageCount)."
            )
            assertEquals(expectedMessageCount, testProcessor.recordCount,
                "More messages on $P2P_OUT_TOPIC were observed that expected.")
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
        val flowConf = """
            session {
                p2pTTL = 500000
            }
        """
        publisher.publish(
            listOf(
                Record(
                    CONFIG_TOPIC,
                    ConfigKeys.FLOW_CONFIG,
                    Configuration(flowConf, flowConf, 0, schemaVersion)
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
            println("Event : $event")
            recordCount++
            if (recordCount > expectedRecordCount) {
                fail("Expected record count exceeded in events processed for this key")
            }
            latch.countDown()

        }
        return emptyList()
    }
}
