package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.publisher.CordaKafkaRPCSenderImpl
import net.corda.messaging.kafka.subscription.CordaAvroDeserializer
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.listener.RPCConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.concurrent.getOrThrow
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class CordaKafkaRPCSenderImplTest {

    private lateinit var cordaSenderImpl: CordaKafkaRPCSenderImpl<String, String>

    private val config: Config = createStandardTestConfig().getConfig(ConfigProperties.PATTERN_RPC_SENDER)

    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var partitionListener: RPCConsumerRebalanceListener<String>

    private val okResponse = listOf<ConsumerRecord<String, RPCResponse>>(
        ConsumerRecord(
            TOPIC_PREFIX + ConfigProperties.TOPIC,
            0,
            0,
            "0",
            RPCResponse(
                "0",
                Instant.now().toEpochMilli(),
                ResponseStatus.OK,
                ByteBuffer.wrap("test".encodeToByteArray())
            )
        )

    )


    private val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
        whenever(it.serialize(any())).thenReturn(ByteBuffer.wrap("test".encodeToByteArray()))
        whenever(it.getClassType(any())).thenReturn(String::class.java)
    }
    private val deserializer = CordaAvroDeserializer(schemaRegistry, mock(), String::class.java)
    private val serializer = CordaAvroSerializer<String>(schemaRegistry)

    @Test
    fun `test rpc response subscription works`() {
        val publisher: Publisher = mock()
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks()

        doAnswer {
            okResponse
        }.whenever(kafkaConsumer).poll()

        doAnswer {
            listOf(TopicPartition(ConfigProperties.TOPIC, 0))
        }.whenever(kafkaConsumer).getPartitions(any(), any())

        lifecycleCoordinator = mock()
        doNothing().whenever(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
        doNothing().whenever(lifecycleCoordinator).updateStatus(LifecycleStatus.DOWN)
        partitionListener = RPCConsumerRebalanceListener("test", "test", lifecycleCoordinator)
        cordaSenderImpl =
            CordaKafkaRPCSenderImpl(
                config,
                publisher,
                consumerBuilder,
                serializer,
                deserializer,
                partitionListener,
                lifecycleCoordinator
            )

        cordaSenderImpl.start()
        partitionListener.onPartitionsAssigned(mutableListOf(TopicPartition("test", 0)))
        verify(lifecycleCoordinator, times(1)).updateStatus(LifecycleStatus.UP)

        partitionListener.onPartitionsRevoked(mutableListOf(TopicPartition("test", 0)))
        verify(lifecycleCoordinator, times(1)).updateStatus(LifecycleStatus.DOWN)
        verify(kafkaConsumer, times(1)).subscribe(eq(listOf("topic.resp")), any())

    }

    @Test
    fun `test send request finishes exceptionally due to lack of partitions`() {
        lifecycleCoordinator = mock()
        partitionListener = RPCConsumerRebalanceListener("test", "test", lifecycleCoordinator)
        cordaSenderImpl =
            CordaKafkaRPCSenderImpl(
                mock(),
                mock(),
                mock(),
                serializer,
                deserializer,
                partitionListener,
                lifecycleCoordinator
            )

        val future = cordaSenderImpl.sendRequest("test")
        assertThrows<CordaRPCAPISenderException> { future.getOrThrow() }
    }

    private fun setupStandardMocks(): Pair<CordaKafkaConsumer<String, RPCResponse>, ConsumerBuilder<String, RPCResponse>> {
        val kafkaConsumer: CordaKafkaConsumer<String, RPCResponse> = mock()
        val consumerBuilder: ConsumerBuilder<String, RPCResponse> = mock()
        doReturn(kafkaConsumer).whenever(consumerBuilder).createRPCConsumer(any(), any(), any(), any())
        doReturn(
            mutableMapOf(
                TopicPartition(ConfigProperties.TOPIC, 0) to 0L,
                TopicPartition(ConfigProperties.TOPIC, 1) to 0L
            )
        ).whenever(kafkaConsumer)
            .beginningOffsets(any())
        return Pair(kafkaConsumer, consumerBuilder)
    }

}
