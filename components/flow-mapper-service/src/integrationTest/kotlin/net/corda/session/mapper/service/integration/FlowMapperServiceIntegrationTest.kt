package net.corda.session.mapper.service.integration

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
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
import net.corda.schema.Schemas.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.ConfigKeys.Companion.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.session.mapper.service.FlowMapperService
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
        const val inputRecordKey = "key"
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
        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, inputRecordKey, FlowMapperEvent(MessageDirection.INBOUND, StartRPCFlow("",
            "",
            "",
            HoldingIdentity("", ""), Instant.now(), "")))))

        publisher.publish(listOf(Record(FLOW_MAPPER_EVENT_TOPIC, inputRecordKey, FlowMapperEvent(MessageDirection.OUTBOUND, SessionEvent(currentTimeMillis(), 3, SessionData())))))

        setupConfig(publisher)

        flowMapperService.start()

        validateOutputTopic(FLOW_EVENT_TOPIC)
        validateOutputTopic(P2P_OUT_TOPIC)

        flowMapperService.stop()
        configService.stop()
    }

    private fun validateOutputTopic(topic: String) {
        val latch = CountDownLatch(1)
        val sub = subscriptionFactory.createDurableSubscription (
            SubscriptionConfig(
                "test", topic
            ), TestProcessor(latch), SmartConfigImpl.empty(),null
        )
        sub.start()
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        sub.stop()
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
