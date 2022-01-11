package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
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

class CordaKafkaRPCSenderImplTest {

    private lateinit var cordaSenderImpl: CordaKafkaRPCSenderImpl<String, String>

    private val config: Config = createStandardTestConfig().getConfig(ConfigProperties.PATTERN_RPC_SENDER)

    private var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private var lifecycleCoordinator: LifecycleCoordinator = mock()
    private val deserializer: CordaAvroDeserializer<String> = mock()
    private val serializer: CordaAvroSerializer<String> = mock()

    @Test
    fun `test send request finishes exceptionally due to lack of partitions`() {
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
        cordaSenderImpl = CordaKafkaRPCSenderImpl(
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
        val kafkaProducer: CordaProducer = mock()
        val cordaProducerBuilder: CordaProducerBuilder = mock()
        doAnswer { kafkaProducer }.whenever(cordaProducerBuilder).createProducer(any())
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())

        cordaSenderImpl = CordaKafkaRPCSenderImpl(
            config,
            mock(),
            cordaProducerBuilder,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )
        cordaSenderImpl.start()
        assertThat(cordaSenderImpl.isRunning).isTrue
        cordaSenderImpl.close()
        verify(kafkaProducer, times(1)).close()
    }
}
