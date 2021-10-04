package net.corda.libs.configuration.read.kafka.factory

import org.mockito.kotlin.mock
import com.typesafe.config.Config
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConfigReaderFactoryImplTest {

    private val config: Config = mock()
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val readServiceFactoryImpl = ConfigReaderFactoryImpl(subscriptionFactory)

    @Test
    fun testCreateRepository() {
        Assertions.assertNotNull(readServiceFactoryImpl.createReader(config))
    }
}
