package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties
import net.corda.messaging.kafka.integration.TopicTemplates
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.processors.TestRPCResponderProcessor
import net.corda.v5.base.concurrent.getOrThrow
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.osgi.test.common.annotation.InjectService

class RPCSubscriptionIntegrationTest {

    private lateinit var rpcConfig: RPCConfig<String, String>
    private lateinit var rpcSender: RPCSender<String, String>
    private lateinit var kafkaConfig: Config
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "integrationTestRPCSender"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var topicAdmin: KafkaTopicAdmin

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = ConfigFactory.empty()
            .withValue(
                IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(
                    IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE
                )
            )
            .withValue(IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
    }

    @Test
    fun `create rpc topics, send request, start rpc sub, respond to request`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC_TEMPLATE)
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC_TEMPLATE)

        rpcConfig = RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, String::class.java, String::class.java)
        rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)
        rpcSender.start()
        val future = rpcSender.sendRequest("REQUEST")

        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCResponderProcessor()
        )
        rpcSub.start()

        Assertions.assertThat(future.getOrThrow()).isEqualTo("RECEIVED and PROCESSED")

        rpcSender.close()
        rpcSub.stop()
    }

}