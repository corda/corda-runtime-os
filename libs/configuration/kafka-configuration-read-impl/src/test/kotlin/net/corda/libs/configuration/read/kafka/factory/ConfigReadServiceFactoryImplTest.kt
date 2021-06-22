package net.corda.libs.configuration.read.kafka.factory

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.Config
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConfigReadServiceFactoryImplTest {

    private val config: Config = mock()
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val readServiceFactoryImpl = ConfigReadServiceFactoryImpl(subscriptionFactory)

    @Test
    fun testCreateRepository() {
        Assertions.assertNotNull(readServiceFactoryImpl.createReadService(config))
    }
}
