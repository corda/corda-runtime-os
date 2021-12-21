package net.corda.session.mapper.service.integration

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.Companion.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.session.mapper.service.FlowMapperService
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.lang.System.currentTimeMillis
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class FlowMapperServiceIntegrationTest {

    private companion object {
        const val clientId = "clientId"
    }
    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var smartConfigFactory: SmartConfigFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var configService: ConfigurationReadService

    @InjectService(timeout = 4000)
    lateinit var flowMapperService: FlowMapperService

    @Test
    fun testFlowMapperService() {
        val publisher = publisherFactory.createPublisher(PublisherConfig(clientId))

        val firstKey = "firstKey"
        val secondKey = "secondKey"
        val firstRpcRecord = FlowMapperEvent(MessageDirection.INBOUND, StartRPCFlow("","","",
            HoldingIdentity("", ""), Instant.now(), ""))

        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, firstKey, firstRpcRecord)))
        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, firstKey, firstRpcRecord)))
        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, firstKey, FlowMapperEvent(MessageDirection.OUTBOUND, SessionEvent(currentTimeMillis(), 3, SessionData())))))
        setupConfig(publisher)
        flowMapperService.start()

        val flowEventLatch = CountDownLatch(3)
        val flowEventSub = subscriptionFactory.createDurableSubscription(SubscriptionConfig("test-output-flow-event-1", FLOW_EVENT_TOPIC),
            TestProcessor(flowEventLatch), SmartConfigImpl.empty(),null)
        flowEventSub.start()

        //validate 2 records sent to flow event
        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, secondKey, firstRpcRecord)))
        eventually(duration = 5.seconds) {
            assertThat(flowEventLatch.count).isEqualTo(1)
        }

        //Cleanup state for first record
        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, firstKey, FlowMapperEvent(MessageDirection.INBOUND, ScheduleCleanup
            (currentTimeMillis())))))

        //validate duplicate wasn't sent to flow event
        assertFalse(flowEventLatch.await(3, TimeUnit.SECONDS))

        //validate p2p out
        val p2pLatch = CountDownLatch(1)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(SubscriptionConfig("test-p2p-out", FLOW_EVENT_TOPIC),
            TestProcessor(p2pLatch), SmartConfigImpl.empty(),null)
        p2pOutSub.start()
        assertTrue(p2pLatch.await(3, TimeUnit.SECONDS))

        //validate first state is cleaned up
        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, firstKey, firstRpcRecord)))
        assertTrue(flowEventLatch.await(5, TimeUnit.SECONDS))

        //validate correct amount of events on the flow mapper event topic(5 rpc records, 1 schedule cleanup, 1 execute cleanup)
        val flowMapperEventLatch = CountDownLatch(7)
        val flowMapperSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("test-mapper-event-count", FLOW_MAPPER_EVENT_TOPIC),
            TestProcessor(flowMapperEventLatch), SmartConfigImpl.empty(),null)
        flowMapperSub.start()
        flowMapperEventLatch.await(10, TimeUnit.SECONDS)

        flowMapperService.stop()
        flowEventSub.stop()
        p2pOutSub.stop()
        flowMapperSub.stop()
        configService.stop()
    }

    private fun setupConfig(publisher: Publisher) {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(bootConf))
        publisher.publish(listOf(Record(CONFIG_TOPIC, FLOW_CONFIG, Configuration(flowConf, "1"))))
        publisher.publish(listOf(Record(CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, "1"))))
        configService.start()
        configService.bootstrapConfig(bootConfig)
    }

    private val bootConf = """
        config.topic.name="config.topic"
        instance-id=1
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
