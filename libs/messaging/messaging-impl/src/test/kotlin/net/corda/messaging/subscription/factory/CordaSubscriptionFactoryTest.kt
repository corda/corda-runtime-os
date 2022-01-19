package net.corda.messaging.subscription.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CordaSubscriptionFactoryTest {

    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
    private val cordaAvroSerializer: CordaAvroSerializer<Any> = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private lateinit var factory: CordaSubscriptionFactory
    private lateinit var config: Config
    private val subscriptionConfig = SubscriptionConfig("group1", "event", 1)

    @BeforeEach
    fun setup() {
        doReturn(cordaAvroSerializer).`when`(cordaAvroSerializationFactory).createAvroSerializer<Any>(any())
        config = ConfigFactory.load()
        factory = CordaSubscriptionFactory(
            cordaAvroSerializationFactory,
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
