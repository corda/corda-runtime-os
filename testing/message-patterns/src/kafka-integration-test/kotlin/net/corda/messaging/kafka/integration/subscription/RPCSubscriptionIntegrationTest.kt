package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties
import net.corda.messaging.kafka.integration.TopicTemplates
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.processors.TestRPCAvroResponderProcessor
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
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

@ExtendWith(ServiceExtension::class)
class RPCSubscriptionIntegrationTest {

    private lateinit var rpcConfig: RPCConfig<String, String>
    private lateinit var rpcSender: RPCSender<String, String>
    private lateinit var kafkaConfig: SmartConfig

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
        kafkaConfig = SmartConfigImpl.empty()
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
    fun `start rpc sender and responder, send avro message, complete correctly`() {
        val rpcConfig =
            RPCConfig(CLIENT_ID, CLIENT_ID, TopicTemplates.RPC_TOPIC, RPCRequest::class.java, RPCResponse::class.java)
        val rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCAvroResponderProcessor()
        )

        rpcSender.start()
        rpcSub.start()
        var responseReceived = false
        var attempts = 5
        val response = RPCResponse(
            "test",
            0L,
            ResponseStatus.OK,
            ByteBuffer.wrap("test".encodeToByteArray())
        )
        while (!responseReceived && attempts > 0) {
            attempts--
            try {
                val future = rpcSender.sendRequest(
                    RPCRequest(
                        "test",
                        0L,
                        "test",
                        0,
                        ByteBuffer.wrap("test".encodeToByteArray())
                    )
                )
                Assertions.assertThat(future.getOrThrow()).isEqualTo(response)
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
        var messageSent = false
        var future = CompletableFuture<String>()
        while (!messageSent && attempts > 0) {
            attempts--
            try {
                future = rpcSender.sendRequest("REQUEST")
                messageSent = true
                rpcSender.close()
            } catch (ex: CordaRPCAPISenderException) {
                Thread.sleep(2000)
            }
        }
        eventually(10.seconds, 1.seconds) {
            assertThrows<CordaRPCAPISenderException> {
                future.getOrThrow()
            }
        }
        rpcSub.stop()
    }
}