package net.corda.flow.session.filter.integration

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.config.Configuration
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.session.filter.FlowSessionFilterService
import net.corda.flow.session.filter.integration.processor.TestFlowSessionFilterProcessor
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
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

@ExtendWith(ServiceExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowFilterServiceIntegrationTest {

    private companion object {
        const val clientId = "clientId"
    }

    private var setup = false

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    // no secrets needed -> empty config
    private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var configService: ConfigurationReadService

    @InjectService(timeout = 4000)
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

    @InjectService(timeout = 4000)
    lateinit var flowSessionFilterService: FlowSessionFilterService

    @BeforeEach
    fun setup() {
        if (!setup) {
            setup = true
            val publisher = publisherFactory.createPublisher(PublisherConfig(clientId))
            setupConfig(publisher)

        }
    }

    @Test
    fun `verify events are forwarded to the correct topic`() {
        flowSessionFilterService.start()
        
        val testId = "test1"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId))

        val flowMapperSerializer = cordaAvroSerializationFactory.createAvroSerializer<FlowMapperEvent> {  }

        val identity = HoldingIdentity(testId, testId)
        val flowHeader = AuthenticatedMessageHeader(identity, identity, 1, "", "", "flowSession")
        val flowEvent = FlowMapperEvent(
            SessionEvent(
                MessageDirection.OUTBOUND, Instant.now(), testId, 1, SessionInit(
                    testId, testId, null, identity,
                    identity, ByteBuffer.wrap("".toByteArray())
                )
            )
        )
        val flowRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(AuthenticatedMessage(flowHeader, ByteBuffer.wrap(flowMapperSerializer.serialize(flowEvent))))
        )

        val invalidHeader = AuthenticatedMessageHeader(identity, identity, 1, "", "", "other")
        val invalidEvent = FlowMapperEvent(ExecuteCleanup())
        val invalidRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(AuthenticatedMessage(invalidHeader, ByteBuffer.wrap(flowMapperSerializer.serialize(invalidEvent))))
        )

        publisher.publish(listOf(flowRecord, flowRecord, invalidRecord))

        //validate mapper receives 2 inits
        val mapperLatch = CountDownLatch(2)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-flow-mapper", FLOW_MAPPER_EVENT_TOPIC),
            TestFlowSessionFilterProcessor("$testId-INITIATED", mapperLatch, 2), SmartConfigImpl.empty(), null
        )
        p2pOutSub.start()
        assertTrue(mapperLatch.await(30, TimeUnit.SECONDS))
        p2pOutSub.stop()
        
        flowSessionFilterService.stop()
    }

    private fun setupConfig(publisher: Publisher) {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(bootConf))
        publisher.publish(listOf(Record(CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, "1"))))
        configService.start()
        configService.bootstrapConfig(bootConfig)
    }

    private val bootConf = """
        instanceId=1
    """

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
