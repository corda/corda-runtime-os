package net.corda.messaging.subscription.factory

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
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
    private val messagingChunkFactory: MessagingChunkFactory = mock()
    private val chunkSerializerService: ChunkSerializerService = mock()
    private lateinit var factory: CordaSubscriptionFactory
    private lateinit var config: SmartConfig
    private lateinit var smartConfigFactory: SmartConfigFactory
    private val subscriptionConfig = SubscriptionConfig("group1", "event")

    @BeforeEach
    fun setup() {
        doReturn(chunkSerializerService).`when`(messagingChunkFactory).createChunkSerializerService(any())
        doReturn(cordaAvroSerializer).`when`(cordaAvroSerializationFactory).createAvroSerializer<Any>(any())
        smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()
        config = smartConfigFactory.create(ConfigFactory.load("config/test.conf"))
        factory = CordaSubscriptionFactory(
            cordaAvroSerializationFactory,
            lifecycleCoordinatorFactory,
            mock(),
            mock(),
            mock(),
            messagingChunkFactory
        )
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    @Test
    fun createCompacted() {
        factory.createCompactedSubscription<Any, Any>(subscriptionConfig, mock(), config)
    }

    @Test
    fun createPubSub() {
        factory.createPubSubSubscription<Any, Any>(subscriptionConfig, mock(), config)
    }

    @Test
    fun createDurableSub() {
        factory.createDurableSubscription<Any, Any>(subscriptionConfig, mock(), config, null)
    }

    @Test
    fun createDurableSubNoInstanceId() {
        assertThrows<CordaMessageAPIFatalException> {
            factory.createDurableSubscription<Any, Any>(
                SubscriptionConfig("group1", "event"),
                mock(), config.withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(null)), null
            )
        }
    }

    @Test
    fun createStateAndEventSub() {
        val subscriptionConfig = SubscriptionConfig("group1", "event")
        factory.createStateAndEventSubscription<Any, Any, Any>(
            subscriptionConfig, mock(), config.withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(100000000))
        )
    }
}
