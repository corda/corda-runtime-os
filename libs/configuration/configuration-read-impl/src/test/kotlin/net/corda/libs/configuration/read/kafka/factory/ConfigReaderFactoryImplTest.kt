package net.corda.libs.configuration.read.kafka.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ConfigReaderFactoryImplTest {

    private val configFactory = mock<SmartConfigFactory>()
    private val config = mock<SmartConfig>() {
        on { factory } doReturn configFactory
    }
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val readServiceFactoryImpl = ConfigReaderFactoryImpl(subscriptionFactory)

    @Test
    fun testCreateRepository() {
        Assertions.assertNotNull(readServiceFactoryImpl.createReader(config))
    }
}
