package net.corda.messaging.kafka.subscription.factory

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.registry.AvroSchemaRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KafkaSubscriptionFactoryTest {

    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private lateinit var factory :KafkaSubscriptionFactory
    private lateinit var config :Config
    private val subscriptionConfig = SubscriptionConfig("group1", "event", 1)

    @BeforeEach
    fun setup() {
        config = ConfigFactory.load()
        factory = KafkaSubscriptionFactory(avroSchemaRegistry)
    }

    @Test
    fun createCompactedSubcreatePubSub() {
        factory.createCompactedSubscription<Any, Any>(subscriptionConfig, mock())
    }

    @Test
    fun createPubSub() {
        factory.createPubSubSubscription<Any, Any>(subscriptionConfig, mock(), null)
    }

    @Test
    fun createDurableSub() {
        factory.createDurableSubscription<Any, Any>(subscriptionConfig, mock(),  ConfigFactory.empty(), null)
    }

    @Test
    fun createDurableSubNoInstanceId() {
        assertThrows<CordaMessageAPIFatalException> { factory.createDurableSubscription<Any, Any>( SubscriptionConfig("group1", "event"), mock(), ConfigFactory.empty(), null) }
    }

    @Test
    fun createStateAndEventSub() {
        val subscriptionConfig = SubscriptionConfig("group1", "event", 1)
        factory.createStateAndEventSubscription<Any, Any, Any>(subscriptionConfig, mock())
    }
}
