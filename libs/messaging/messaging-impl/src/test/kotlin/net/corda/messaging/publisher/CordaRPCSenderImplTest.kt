package net.corda.messaging.publisher

import com.typesafe.config.Config
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.createStandardTestConfig
import net.corda.messaging.properties.ConfigProperties.Companion.PATTERN_RPC_SENDER
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
import net.corda.v5.base.concurrent.getOrThrow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CordaRPCSenderImplTest {

    private lateinit var cordaSenderImpl: CordaRPCSenderImpl<String, String>

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_RPC_SENDER)

    private var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private var lifecycleCoordinator: LifecycleCoordinator = mock()
    private val deserializer: CordaAvroDeserializer<String> = mock()
    private val serializer: CordaAvroSerializer<String> = mock()

    @Test
    fun `test send request finishes exceptionally due to lack of partitions`() {
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
        cordaSenderImpl = CordaRPCSenderImpl(
            config,
            mock(),
            mock(),
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        val future = cordaSenderImpl.sendRequest("test")
        assertThrows<CordaRPCAPISenderException> { future.getOrThrow() }
    }

    @Test
    fun `test producer is closed properly`() {
        val cordaProducer: CordaProducer = mock()
        val cordaConsumer: CordaConsumer<Any, Any> = mock()
        val cordaProducerBuilder: CordaProducerBuilder = mock()
        val cordaConsumerBuilder: CordaConsumerBuilder = mock()
        doAnswer { cordaProducer }.whenever(cordaProducerBuilder).createProducer(any())
        doAnswer { cordaConsumer }.whenever(cordaConsumerBuilder).createRPCConsumer<Any, Any>(any(), any(), any(), any())
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())

        cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )
        cordaSenderImpl.start()
        assertThat(cordaSenderImpl.isRunning).isTrue
        cordaSenderImpl.close()
        verify(cordaProducer, times(1)).close()
    }
}
