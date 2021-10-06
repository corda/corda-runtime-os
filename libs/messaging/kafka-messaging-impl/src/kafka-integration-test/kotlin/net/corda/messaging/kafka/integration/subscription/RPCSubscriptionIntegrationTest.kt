package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties
import net.corda.messaging.kafka.integration.TopicTemplates
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.processors.TestRPCCancelResponderProcessor
import net.corda.messaging.kafka.integration.processors.TestRPCErrorResponderProcessor
import net.corda.messaging.kafka.integration.processors.TestRPCResponderProcessor
import net.corda.messaging.kafka.integration.processors.TestRPCUnresponsiveResponderProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CancellationException

@ExtendWith(ServiceExtension::class)
class RPCSubscriptionIntegrationTest {

    private lateinit var rpcConfig: RPCConfig<String, String>
    private lateinit var rpcSender: RPCSender<String, String>
    private lateinit var kafkaConfig: Config

    private companion object {
        const val CLIENT_ID = "integrationTestRPCSender"

        @InjectService(timeout = 4000)
        lateinit var topicAdmin: KafkaTopicAdmin

        private val kafkaProperties = getKafkaProperties()

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC_TEMPLATE)
            topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC_TEMPLATE)
        }
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

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
    fun `start rpc sender and responder, send message, complete correctly`() {
        rpcConfig = RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, String::class.java, String::class.java)
        rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCResponderProcessor()
        )

        rpcSender.start()
        rpcSub.start()
        var responseReceived = false
        var attempts = 5
        while (!responseReceived && attempts > 0) {
            attempts--
            try {
                val future = rpcSender.sendRequest("REQUEST")
                Assertions.assertThat(future.getOrThrow()).isEqualTo("RECEIVED and PROCESSED")
                responseReceived = true
            } catch (ex: CordaRPCAPISenderException) {
                Thread.sleep(2000)
            }
        }

        if(!responseReceived) {
            fail("Failed to get a response for the request")
        }

        rpcSender.close()
        rpcSub.stop()
    }

    @Test
    fun `start rpc sender and responder, send message, complete exceptionally`() {
        rpcConfig = RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, String::class.java, String::class.java)
        rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCErrorResponderProcessor()
        )

        rpcSender.start()
        rpcSub.start()
        var responseReceived = false
        var attempts = 5
        while (!responseReceived && attempts > 0) {
            attempts--
            try {
                val future = rpcSender.sendRequest("REQUEST")
                future.getOrThrow()
            } catch (ex: CordaRPCAPIResponderException) {
                responseReceived = true
            } catch (ex: CordaRPCAPISenderException) {
                Thread.sleep(2000)
            }
        }

        if (!responseReceived) {
            fail("Failed to get a response for the request")
        }

        rpcSender.close()
        rpcSub.stop()
    }

    @Test
    fun `start rpc sender and responder, send message, complete with cancellation`() {
        rpcConfig = RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, String::class.java, String::class.java)
        rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCCancelResponderProcessor()
        )

        rpcSender.start()
        rpcSub.start()
        var responseReceived = false
        var attempts = 5
        while (!responseReceived && attempts > 0) {
            attempts--
            try {
                val future = rpcSender.sendRequest("REQUEST")
                future.getOrThrow()
            } catch (ex: CordaRPCAPISenderException) {
                Thread.sleep(2000)
            } catch (ex: CancellationException) {
                responseReceived = true
            }
        }

        if (!responseReceived) {
            fail("Failed to get a response for the request")
        }

        rpcSender.close()
        rpcSub.stop()
    }

    @Test
    fun `start rpc sender and responder, send message, complete exceptionally due to repartition`() {
        rpcConfig = RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, String::class.java, String::class.java)
        rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCUnresponsiveResponderProcessor()
        )

        rpcSender.start()
        rpcSub.start()
        var attempts = 5
        while (attempts > 0) {
            attempts--
            try {
                val future = rpcSender.sendRequest("REQUEST")
                rpcSender.close()
                eventually(10.seconds, 1.seconds) {
                    assertThrows<CordaRPCAPISenderException> {
                        future.getOrThrow()
                    }
                }
            } catch (ex: CordaRPCAPISenderException) {
                Thread.sleep(2000)
            }
        }
        rpcSub.stop()
    }
}