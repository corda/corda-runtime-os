package net.corda.messaging.publisher

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.test.util.eventually
import net.corda.v5.base.concurrent.getOrThrow
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

    private val config = createResolvedSubscriptionConfig(SubscriptionType.RPC_SENDER)

    @Test
    fun `test send request finishes exceptionally due to lack of partitions`() {
        val deserializer: CordaAvroDeserializer<String> = mock()
        val serializer: CordaAvroSerializer<String> = mock()
        val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
        val lifecycleCoordinator: LifecycleCoordinator = mock()
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
        val cordaSenderImpl = CordaRPCSenderImpl(
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
        val deserializer: CordaAvroDeserializer<String> = mock()
        val serializer: CordaAvroSerializer<String> = mock()
        val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
        val lifecycleCoordinator: LifecycleCoordinator = mock()
        val cordaProducer: CordaProducer = mock()
        val cordaConsumer: CordaConsumer<Any, Any> = mock()
        val cordaProducerBuilder: CordaProducerBuilder = mock()
        val cordaConsumerBuilder: MessageBusConsumerBuilder = mock()
        doAnswer { cordaProducer }.whenever(cordaProducerBuilder).createProducer(any(), any())
        doAnswer { cordaConsumer }.whenever(cordaConsumerBuilder).createConsumer<Any, Any>(any(), any(), any(), any(), any())
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )
        cordaSenderImpl.start()
        eventually {
            verify(cordaProducerBuilder).createProducer(any(), config.busConfig)
        }
        cordaSenderImpl.close()
        verify(cordaProducer, times(1)).close()
    }
}
