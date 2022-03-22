package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class RPCSubscriptionIntegrationTest {

    private lateinit var rpcConfig: RPCConfig<String, String>
    private lateinit var kafkaConfig: SmartConfig
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

    @InjectService(timeout = 4000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(
                IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(
                    IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE
                )
            )
            .withValue(
                IntegrationTestProperties.TOPIC_PREFIX,
                ConfigValueFactory.fromAnyRef(TopicTemplates.TEST_TOPIC_PREFIX)
            )
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `start rpc sender and responder, send message, complete correctly`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC1_TEMPLATE)
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC1_TEMPLATE)

        rpcConfig = RPCConfig(
            CLIENT_ID + 1,
            CLIENT_ID,
            TopicTemplates.RPC_TOPIC1,
            String::class.java,
            String::class.java
        )
        val rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCResponderProcessor()
        )

        val coordinator1 =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("rpcSenderTest"))
            { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
                when (event) {
                    is RegistrationStatusChangeEvent -> {
                        if (event.status == LifecycleStatus.UP) {
                            coordinator.updateStatus(LifecycleStatus.UP)
                        } else {
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                }
            }
        val coordinator2 =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("rpcReceiverTest"))
            { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
                when (event) {
                    is RegistrationStatusChangeEvent -> {
                        if (event.status == LifecycleStatus.UP) {
                            coordinator.updateStatus(LifecycleStatus.UP)
                        } else {
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                }
            }
        coordinator1.start()
        coordinator2.start()

        coordinator1.followStatusChangesByName(setOf(rpcSender.subscriptionName))
        coordinator2.followStatusChangesByName(setOf(rpcSub.subscriptionName))
        rpcSender.start()
        rpcSub.start()

        eventually(duration = 10.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator1.status)
            assertEquals(LifecycleStatus.UP, coordinator2.status)
        }

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

        if (!responseReceived) {
            fail("Failed to get a response for the request")
        }

        rpcSender.close()
        rpcSub.stop()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator1.status)
            assertEquals(LifecycleStatus.DOWN, coordinator2.status)
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `start rpc sender and responder, send avro message, complete correctly`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC2_TEMPLATE)
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC2_TEMPLATE)

        val rpcConfig = RPCConfig(
            CLIENT_ID + 2,
            CLIENT_ID,
            TopicTemplates.RPC_TOPIC2,
            RPCRequest::class.java,
            RPCResponse::class.java
        )
        val rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)
        val timestamp = Instant.ofEpochMilli(0L)
        val rpcSub = subscriptionFactory.createRPCSubscription(
            rpcConfig, kafkaConfig, TestRPCAvroResponderProcessor(timestamp)
        )

        rpcSender.start()
        rpcSub.start()
        var responseReceived = false
        var attempts = 5
        val response = RPCResponse(
            "test",
            timestamp,
            ResponseStatus.OK,
            ByteBuffer.wrap("test".encodeToByteArray())
        )
        while (!responseReceived && attempts > 0) {
            attempts--
            try {
                val future = rpcSender.sendRequest(
                    RPCRequest(
                        "test",
                        Instant.ofEpochMilli(0L),
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
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `start rpc sender and responder, send message, complete exceptionally`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC3_TEMPLATE)
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC3_TEMPLATE)

        rpcConfig = RPCConfig(
            CLIENT_ID + 3,
            CLIENT_ID,
            TopicTemplates.RPC_TOPIC3,
            String::class.java,
            String::class.java
        )
        val rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

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
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `start rpc sender and responder, send message, complete with cancellation`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC4_TEMPLATE)
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC4_TEMPLATE)
        rpcConfig = RPCConfig(
            CLIENT_ID + 4,
            CLIENT_ID,
            TopicTemplates.RPC_TOPIC4,
            String::class.java,
            String::class.java
        )
        val rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

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
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `start rpc sender and responder, send message, complete exceptionally due to repartition`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_TOPIC5_TEMPLATE)
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.RPC_RESPONSE_TOPIC5_TEMPLATE)
        rpcConfig = RPCConfig(
            CLIENT_ID + 5,
            CLIENT_ID,
            TopicTemplates.RPC_TOPIC5,
            String::class.java,
            String::class.java
        )
        val rpcSender = publisherFactory.createRPCSender(rpcConfig, kafkaConfig)

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
