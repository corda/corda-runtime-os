package net.corda.flow.p2p.filter.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.flow.p2p.filter.FlowP2PFilterService
import net.corda.flow.p2p.filter.integration.processor.TestFlowSessionFilterProcessor
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowFilterServiceIntegrationTest {

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
    lateinit var flowSessionFilterService: FlowP2PFilterService

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
    fun `verify events are forwarded to the correct topic`() {
        flowSessionFilterService.start()

        val testId = "test1"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), bootConfig)

        val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }
        val flowEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<FlowEvent> { }

        // Test config updates don't break Flow Session Filter Service
        republishConfig(publisher)

        val identity = HoldingIdentity(testId, testId)
        val flowHeader = AuthenticatedMessageHeader(
            identity, identity, Instant.ofEpochMilli(1), "", "", "flowSession", MembershipStatusFilter.ACTIVE
        )
        val version = listOf(1)
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND, Instant.now(), testId, 1, identity, identity, 0, listOf(), SessionInit(
                testId,
                version,
                testId,
                null,
                emptyKeyValuePairList(),
                emptyKeyValuePairList(),
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

        val invalidHeader = AuthenticatedMessageHeader(
            identity, identity, Instant.ofEpochMilli(1), "", "", "other", MembershipStatusFilter.ACTIVE
        )
        val invalidEvent = FlowEvent(testId, sessionEvent)
        val invalidRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(
                AuthenticatedMessage(
                    invalidHeader, ByteBuffer.wrap(flowEventSerializer.serialize(invalidEvent))
                )
            )
        )

        publisher.publish(listOf(sessionRecord, sessionRecord, invalidRecord))

        //validate mapper receives 2 inits
        val mapperLatch = CountDownLatch(2)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-flow-mapper", FLOW_MAPPER_EVENT_TOPIC),
            TestFlowSessionFilterProcessor("$testId-INITIATED", mapperLatch, 2),
            bootConfig,
            null
        )
        p2pOutSub.start()
        assertTrue(mapperLatch.await(30, TimeUnit.SECONDS))
        p2pOutSub.close()

        flowSessionFilterService.stop()
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
