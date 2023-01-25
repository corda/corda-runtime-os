package net.corda.configuration.write.impl.tests.writer

import com.typesafe.config.ConfigFactory
import net.corda.configuration.write.impl.writer.CLIENT_NAME_RPC
import net.corda.configuration.write.impl.writer.GROUP_NAME
import net.corda.configuration.write.impl.writer.RPCSubscriptionFactory
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [RPCSubscriptionFactory]. */
class RPCSubscriptionFactoryTests {
    private val configFactory = SmartConfigFactory.createWithoutSecurityServices()

    /** Returns a mock [SubscriptionFactory]. */
    private fun getSubscriptionFactory() = mock<SubscriptionFactory>().apply {
        whenever(createRPCSubscription<Any, Any>(any(), any(), any())).doReturn(mock())
    }

    /** Returns a mock [ConfigurationValidatorFactory]. */
    private fun getConfigValidatorFactory() = mock<ConfigurationValidatorFactory>().apply {
        whenever(createConfigValidator()).thenReturn(mock())
    }

    val dbConnectionManager = mock<DbConnectionManager>().also {
        whenever(it.getClusterEntityManagerFactory()).thenReturn(mock())
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
        val configWriterFactory = RPCSubscriptionFactory(subscriptionFactory, getConfigValidatorFactory(), dbConnectionManager, mock())
        configWriterFactory.create(expectedConfig)

        verify(subscriptionFactory).createRPCSubscription(eq(expectedRPCConfig), eq(expectedConfig), any())
    }
}
