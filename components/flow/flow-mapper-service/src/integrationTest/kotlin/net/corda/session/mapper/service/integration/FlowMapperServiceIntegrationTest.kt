package net.corda.session.mapper.service.integration

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.session.mapper.service.FlowMapperService
import net.corda.test.flow.util.buildSessionEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.lang.System.currentTimeMillis
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowMapperServiceIntegrationTest {

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
    lateinit var flowMapperService: FlowMapperService

    @BeforeEach
    fun setup() {
        if (!setup) {
            setup = true
            val publisher = publisherFactory.createPublisher(PublisherConfig(clientId))
            setupConfig(publisher)
            flowMapperService.start()
        }
    }

    @Test
    fun testSessionInitOutAndDataInbound() {
        val testId = "test1"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId))

        //send 2 session init, 1 is duplicate
        val identity = HoldingIdentity(testId, testId)
        val flowKey = FlowKey(testId, HoldingIdentity(testId, testId))
        val sessionInitEvent = Record<Any, Any>(
            FLOW_MAPPER_EVENT_TOPIC, testId, FlowMapperEvent(
                buildSessionEvent(MessageDirection.OUTBOUND, testId, 1, SessionInit(
                    testId, testId, flowKey,null
                ))
            )
        )

        publisher.publish(listOf(sessionInitEvent, sessionInitEvent))

        //validate p2p out only receives 1 init
        val p2pLatch = CountDownLatch(1)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-p2p-out", P2P_OUT_TOPIC),
            TestP2POutProcessor(testId, p2pLatch, 1), SmartConfigImpl.empty(), null
        )
        p2pOutSub.start()
        assertTrue(p2pLatch.await(10, TimeUnit.SECONDS))
        p2pOutSub.stop()

        //send data back
        val sessionDataEvent = Record<Any, Any>(
            FLOW_MAPPER_EVENT_TOPIC, testId, FlowMapperEvent(
                buildSessionEvent(MessageDirection.INBOUND, testId, 2, SessionData())
            )
        )
        publisher.publish(listOf(sessionDataEvent))

        //validate flow event topic
        val flowEventLatch = CountDownLatch(1)
        val flowEventSub = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$testId-flow-event", FLOW_EVENT_TOPIC),
            TestFlowMessageProcessor(flowEventLatch, identity, 1), SmartConfigImpl.empty(), null
        )
        flowEventSub.start()
        assertTrue(flowEventLatch.await(5, TimeUnit.SECONDS))
        flowEventSub.stop()
    }

    @Test
    fun testStartRPCDuplicatesAndCleanup() {
        val testId = "test2"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId))

        //2 startRPCRecord, 1 duplicate
        val identity = HoldingIdentity(testId, testId)
        val context = FlowStartContext(
            FlowStatusKey("clientId", identity),
            FlowInitiatorType.RPC,
            "clientId",
            identity,
            "cpi id",
            identity,
            "class name",
            Instant.now())

        val startRPCEvent = Record<Any, Any>(
            FLOW_MAPPER_EVENT_TOPIC, testId, FlowMapperEvent(
                StartFlow(
                    context,
                    null
                )
            )
        )
        publisher.publish(listOf(startRPCEvent, startRPCEvent))

        //flow event subscription to validate outputs
        val flowEventLatch = CountDownLatch(2)
        val flowEventSub = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$testId-flow-event", FLOW_EVENT_TOPIC),
            TestFlowMessageProcessor(flowEventLatch, identity, 2), SmartConfigImpl.empty(), null
        )
        flowEventSub.start()

        //cleanup
        val cleanup = Record<Any, Any>(
            FLOW_MAPPER_EVENT_TOPIC, testId, FlowMapperEvent(
                ScheduleCleanup(currentTimeMillis())
            )
        )
        publisher.publish(listOf(cleanup))

        //assert duplicate start rpc didn't get processed (and also give Execute cleanup time to run)
        assertFalse(flowEventLatch.await(3, TimeUnit.SECONDS))

        //send same key start rpc again
        publisher.publish(listOf(startRPCEvent))

        //validate went through and not a duplicate
        assertTrue(flowEventLatch.await(5, TimeUnit.SECONDS))

        flowEventSub.stop()
    }

    @Test
    fun testNoStateForMapper() {
        val testId = "test3"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId))

        //send data, no state
        val sessionDataEvent = Record<Any, Any>(
            FLOW_MAPPER_EVENT_TOPIC, testId, FlowMapperEvent(
                buildSessionEvent(MessageDirection.OUTBOUND, testId, 1, SessionData())
            )
        )
        publisher.publish(listOf(sessionDataEvent))

        //validate p2p out doesn't have any records
        val p2pLatch = CountDownLatch(1)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-p2p-out", P2P_OUT_TOPIC),
            TestP2POutProcessor(testId, p2pLatch, 0), SmartConfigImpl.empty(), null
        )
        p2pOutSub.start()
        assertFalse(p2pLatch.await(3, TimeUnit.SECONDS))
        p2pOutSub.stop()
    }

    private fun setupConfig(publisher: Publisher) {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(bootConf))
        publisher.publish(listOf(Record(CONFIG_TOPIC, FLOW_CONFIG, Configuration(flowConf, "1"))))
        publisher.publish(listOf(Record(CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, "1"))))
        configService.start()
        configService.bootstrapConfig(bootConfig)
    }

    private val bootConf = """
        instanceId=1
    """

    private val flowConf = """
            componentVersion="5.1"
            consumer {
                topic = "flow.event.topic"
                group = "FlowEventConsumer"
            }
            mapper {
                topic {
                    flowMapperEvent = "flow.mapper.event.topic"
                    p2pout = "p2p.out"
                }
                consumer {
                    group = "FlowMapperConsumer"
                }
            }
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
