package net.corda.messaging.kafka.subscription.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.subscription.factory.CordaSubscriptionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CordaSubscriptionFactoryTest {

    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
    private val publisherFactory: PublisherFactory = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private lateinit var factory: CordaSubscriptionFactory
    private lateinit var config: Config
    private val subscriptionConfig = SubscriptionConfig("group1", "event", 1)

    @BeforeEach
    fun setup() {
        config = ConfigFactory.load()
        factory = CordaSubscriptionFactory(
            cordaAvroSerializationFactory,
            publisherFactory,
            lifecycleCoordinatorFactory,
            mock(),
            mock(),
            mock()
        )
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    @Test
    fun createCompacted() {
        factory.createCompactedSubscription<Any, Any>(subscriptionConfig, mock())
    }

    @Test
    fun createPubSub() {
        factory.createPubSubSubscription<Any, Any>(subscriptionConfig, mock(), null)
    }

    @Test
    fun createDurableSub() {
        factory.createDurableSubscription<Any, Any>(subscriptionConfig, mock(), SmartConfigImpl.empty(), null)
    }

    @Test
    fun createDurableSubNoInstanceId() {
        assertThrows<CordaMessageAPIFatalException> {
            factory.createDurableSubscription<Any, Any>(
                SubscriptionConfig("group1", "event"),
                mock(), SmartConfigImpl.empty(), null
            )
        }
    }

    @Test
    fun createStateAndEventSub() {
        val subscriptionConfig = SubscriptionConfig("group1", "event", 1)
        factory.createStateAndEventSubscription<Any, Any, Any>(subscriptionConfig, mock())
    }
}
