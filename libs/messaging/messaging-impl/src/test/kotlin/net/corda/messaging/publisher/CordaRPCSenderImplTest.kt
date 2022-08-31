package net.corda.messaging.publisher

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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
import java.util.concurrent.CompletableFuture
import javax.security.auth.callback.ConfirmationCallback.OK

class CordaRPCSenderImplTest {

    private val config = createResolvedSubscriptionConfig(SubscriptionType.RPC_SENDER)
    private val thread = mock<Thread>()
    private val block = argumentCaptor<() -> Unit>()
    private val threadFactory = mock<(() -> Unit) -> Thread> {
        on { invoke(block.capture()) } doReturn thread
    }

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
            lifecycleCoordinatorFactory,
            threadFactory,
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
        val cordaConsumerBuilder: CordaConsumerBuilder = mock()
        doAnswer { cordaProducer }.whenever(cordaProducerBuilder).createProducer(any(), any())
        doAnswer { cordaConsumer }.whenever(cordaConsumerBuilder)
            .createConsumer<Any, Any>(any(), any(), any(), any(), any(), any())
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory,
            threadFactory,
        )
        cordaSenderImpl.start()
        block.firstValue.invoke()
        verify(cordaProducerBuilder).createProducer(any(), eq(config.messageBusConfig))

        cordaSenderImpl.close()
        verify(cordaProducer, times(1)).close()
    }

    @Test
    fun `send returns the correct reply`() {
        val partitionListener = argumentCaptor<CordaConsumerRebalanceListener>()
        val consumer = mock<CordaConsumer<String, RPCResponse>>()
        doNothing().whenever(consumer).subscribe(any<Collection<String>>(), partitionListener.capture())
        val cordaConsumerBuilder = mock<CordaConsumerBuilder> {
            on {
                createConsumer(
                    any(),
                    any(),
                    eq(String::class.java),
                    eq(RPCResponse::class.java),
                    any(),
                    anyOrNull(),
                )
            } doReturn consumer
        }
        val sentRecords = argumentCaptor<List<CordaProducerRecord<*, *>>>()
        val producer = mock<CordaProducer>()
        doNothing().whenever(producer).sendRecords(sentRecords.capture())
        val cordaProducerBuilder = mock<CordaProducerBuilder> {
            on { createProducer(any(), any()) } doReturn producer
        }
        val correctData = byteArrayOf(8)
        val expectedReply = "Yep"
        val deserializer = mock<CordaAvroDeserializer<String>> {
            on { deserialize(correctData) } doReturn expectedReply
        }
        val serializer = mock<CordaAvroSerializer<String>> {
            on { serialize(any()) } doReturn byteArrayOf(1, 2, 3)
        }
        val lifecycleCoordinator = mock<LifecycleCoordinator>()
        val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
            on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
        }
        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory,
            threadFactory,
        )
        var future: CompletableFuture<String>? = null
        whenever(consumer.poll(any())).thenAnswer {
            partitionListener.firstValue.onPartitionsAssigned(
                listOf(
                    CordaTopicPartition("", 1)
                )
            )
            future = cordaSenderImpl.sendRequest("Hello")
            emptyList<CordaConsumerRecord<String, RPCResponse>>()
        }
            .thenAnswer {
                val sent = sentRecords.firstValue.first()
                val value = sent.value as RPCRequest
                val wrongKey = CordaConsumerRecord(
                    topic = sent.topic,
                    partition = 1,
                    offset = 1L,
                    key = "Another Key",
                    value = RPCResponse(
                        value.sender,
                        "Another Key",
                        Instant.ofEpochSecond(100),
                        ResponseStatus.OK,
                        ByteBuffer.wrap(byteArrayOf(4, 5, 6)),
                    ),
                    timestamp = 100L
                )
                val wrongSender = CordaConsumerRecord(
                    topic = sent.topic,
                    partition = 1,
                    offset = 1L,
                    key = sent.key.toString(),
                    value = RPCResponse(
                        "another sender",
                        value.correlationKey,
                        Instant.ofEpochSecond(100),
                        ResponseStatus.OK,
                        ByteBuffer.wrap(byteArrayOf(7)),
                    ),
                    timestamp = 100L
                )
                val correct = CordaConsumerRecord(
                    topic = sent.topic,
                    partition = 1,
                    offset = 1L,
                    key = sent.key.toString(),
                    value = RPCResponse(
                        value.sender,
                        value.correlationKey,
                        Instant.ofEpochSecond(100),
                        ResponseStatus.OK,
                        ByteBuffer.wrap(correctData),
                    ),
                    timestamp = 100L
                )
                listOf(
                    wrongKey,
                    wrongSender,
                    correct,
                )
            }.thenThrow(CordaRuntimeException("Stop"))
        cordaSenderImpl.start()
        block.firstValue.invoke()

        assertThat(future)
            .isNotNull
            .isCompletedWithValue(expectedReply)
    }
}
