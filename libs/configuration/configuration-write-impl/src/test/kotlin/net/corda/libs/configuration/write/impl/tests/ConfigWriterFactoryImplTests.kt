package net.corda.libs.configuration.write.impl.tests

import com.typesafe.config.ConfigFactory
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.write.impl.CLIENT_NAME_DB
import net.corda.libs.configuration.write.impl.CLIENT_NAME_RPC
import net.corda.libs.configuration.write.impl.ConfigWriterFactoryImpl
import net.corda.libs.configuration.write.impl.GROUP_NAME
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [ConfigWriterFactoryImpl]. */
class ConfigWriterFactoryImplTests {
    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())

    /** Returns a mock [SubscriptionFactory]. */
    private fun getSubscriptionFactory() = mock<SubscriptionFactory>().apply {
        whenever(createRPCSubscription<Any, Any>(any(), any(), any())).doReturn(mock())
    }

    /** Returns a mock [PublisherFactory]. */
    private fun getPublisherFactory() = mock<PublisherFactory>().apply {
        whenever(createPublisher(any(), any())).thenReturn(mock())
    }

    @Test
    fun `factory does not start the config writer`() {
        val configWriterFactory = ConfigWriterFactoryImpl(getSubscriptionFactory(), getPublisherFactory())
        val configWriter = configWriterFactory.create(mock(), 0, mock())
        assertFalse(configWriter.isRunning)
    }

    @Test
    fun `factory creates a publisher with the correct configuration`() {
        val expectedPublisherConfig = PublisherConfig(CLIENT_NAME_DB, 777)
        val expectedConfig = configFactory.create(ConfigFactory.parseMap(mapOf("dummyKey" to "dummyValue")))

        val publisherFactory = getPublisherFactory()
        val configWriterFactory = ConfigWriterFactoryImpl(getSubscriptionFactory(), publisherFactory)
        configWriterFactory.create(expectedConfig, expectedPublisherConfig.instanceId!!, mock())

        verify(publisherFactory).createPublisher(expectedPublisherConfig, expectedConfig)
    }

    @Test
    fun `factory creates an RPC subscription with the correct configuration`() {
        val expectedRPCConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java,
        )
        val expectedConfig = configFactory.create(ConfigFactory.parseMap(mapOf("dummyKey" to "dummyValue")))

        val subscriptionFactory = getSubscriptionFactory()
        val configWriterFactory = ConfigWriterFactoryImpl(subscriptionFactory, getPublisherFactory())
        configWriterFactory.create(expectedConfig, 777, mock())

        verify(subscriptionFactory).createRPCSubscription(eq(expectedRPCConfig), eq(expectedConfig), any())
    }
}
