package net.corda.libs.configuration.write.impl.tests

import com.typesafe.config.ConfigFactory
import net.corda.config.schema.Schema
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.libs.configuration.write.impl.CLIENT_NAME_DB
import net.corda.libs.configuration.write.impl.CLIENT_NAME_RPC
import net.corda.libs.configuration.write.impl.ConfigWriterFactoryImpl
import net.corda.libs.configuration.write.impl.ConfigWriterProcessor
import net.corda.libs.configuration.write.impl.GROUP_NAME
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests of [ConfigWriterFactoryImpl]. */
class ConfigWriterFactoryImplTests {
    private val dummyInstanceId = 777
    private val dummyConfig = SmartConfigFactoryImpl().create(
        ConfigFactory.parseMap(mapOf("dummyKey" to "dummyValue"))
    )

    // A mock `SubscriptionFactory` that returns `DummyRPCSubscription`s.
    private val dummySubscriptionFactory = mock<SubscriptionFactory>().apply {
        whenever(
            createRPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>(any(), any(), any())
        ).doReturn(DummyRPCSubscription())
    }

    // A mock `PublisherFactory` that returns `DummyPublisherSubscription`s.
    private val dummyPublisherFactory = mock<PublisherFactory>().apply {
        whenever(createPublisher(any(), any())).thenReturn(DummyPublisher())
    }

    @Test
    fun `factory does not start the config writer`() {
        val configWriterFactory = ConfigWriterFactoryImpl(dummySubscriptionFactory, dummyPublisherFactory)
        val configWriter = configWriterFactory.create(dummyConfig, dummyInstanceId, mock())
        assertFalse(configWriter.isRunning)
    }

    @Test
    fun `factory creates a publisher with the correct configuration`() {
        val expectedPublisherConfig = PublisherConfig(CLIENT_NAME_DB, dummyInstanceId)

        var publisherConfig: PublisherConfig? = null
        var kafkaConfig: SmartConfig? = null

        // A mock `PublisherFactory` that captures the argument to the creation of a `DummyPublisherSubscription`.
        val capturingPublisherFactory = mock<PublisherFactory>().apply {
            whenever(createPublisher(any(), any())).doAnswer { invocation ->
                publisherConfig = invocation.getArgument(0)
                kafkaConfig = invocation.getArgument(1)
                DummyPublisher()
            }
        }

        val configWriterFactory = ConfigWriterFactoryImpl(dummySubscriptionFactory, capturingPublisherFactory)
        configWriterFactory.create(dummyConfig, dummyInstanceId, mock())

        assertEquals(expectedPublisherConfig, publisherConfig)
        assertEquals(dummyConfig, kafkaConfig)
    }

    @Test
    fun `factory creates an RPC subscription with the correct configuration`() {
        val expectedRPCConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            Schema.CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java,
            dummyInstanceId
        )

        var rpcConfig: RPCConfig<Any, Any>? = null
        var nodeConfig: SmartConfig? = null
        var processor: RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null

        // A mock `SubscriptionFactory` that captures the argument to the creation of a `DummyRPCSubscription`.
        val capturingSubscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(
                createRPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>(any(), any(), any())
            ).doAnswer { invocation ->
                rpcConfig = invocation.getArgument(0)
                nodeConfig = invocation.getArgument(1)
                processor = invocation.getArgument(2)
                DummyRPCSubscription()
            }
        }

        val configWriterFactory = ConfigWriterFactoryImpl(capturingSubscriptionFactory, dummyPublisherFactory)
        configWriterFactory.create(dummyConfig, dummyInstanceId, mock())

        assertEquals(expectedRPCConfig, rpcConfig)
        assertEquals(dummyConfig, nodeConfig)
        assertTrue(processor is ConfigWriterProcessor)
    }
}