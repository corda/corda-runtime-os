package net.corda.messaging.publisher

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIAuthException
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.messaging.subscription.LifeCycleCoordinatorMockHelper
import net.corda.messaging.utils.FutureTracker
import net.corda.test.util.waitWhile
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CordaRPCSenderImplTest {

    private val config = createResolvedSubscriptionConfig(SubscriptionType.RPC_SENDER)

    private companion object {
        private const val TEST_TIMEOUT_SECONDS = 30L
    }

    private val deserializer: CordaAvroDeserializer<String> = mock()
    private val serializer: CordaAvroSerializer<String> = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifeCycleCoordinatorMockHelper = LifeCycleCoordinatorMockHelper()
    private val cordaProducer: CordaProducer = mock()
    private val futureTracker = spy<FutureTracker<String>>()
    private val cordaConsumer = mock<CordaConsumer<String, RPCResponse>>()
    private val cordaProducerBuilder: CordaProducerBuilder = mock()
    private val cordaConsumerBuilder: CordaConsumerBuilder = mock()

    @BeforeEach
    fun setup() {
        doReturn(lifeCycleCoordinatorMockHelper.lifecycleCoordinator).`when`(lifecycleCoordinatorFactory)
            .createCoordinator(any(), any())
    }

    @Test
    fun `test send request finishes exceptionally due to lack of partitions`() {
        doReturn(lifeCycleCoordinatorMockHelper.lifecycleCoordinator).`when`(lifecycleCoordinatorFactory)
            .createCoordinator(any(), any())
        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            mock(),
            mock(),
            serializer,
            deserializer,
            futureTracker,
            lifecycleCoordinatorFactory,
        )

        val future = cordaSenderImpl.sendRequest("test")
        assertThatThrownBy { future.getOrThrow() }
            .isInstanceOf(CordaRPCAPISenderException::class.java)
            .hasMessageContaining("No partitions assigned for topic")
    }

    @Test
    fun `test producer is closed properly`() {
        doAnswer { cordaProducer }.whenever(cordaProducerBuilder).createProducer(any(), any(), anyOrNull())
        doThrow(CordaMessageAPIFatalException("Bail out here")).whenever(cordaConsumerBuilder)
            .createConsumer<Any, Any>(any(), any(), any(), any(), any(), any())

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            futureTracker,
            lifecycleCoordinatorFactory
        )
        cordaSenderImpl.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { cordaSenderImpl.isRunning }

        verify(cordaProducerBuilder).createProducer(any(), eq(config.messageBusConfig), anyOrNull())

        cordaSenderImpl.close()
        verify(cordaProducer, times(1)).close()
        verify(futureTracker, times(1)).close()
        assertThat(lifeCycleCoordinatorMockHelper.lifecycleCoordinatorThrows).isFalse()
    }

    @Test
    fun `subscription fails and resources are closed when no partitions can be assigned`() {
        doAnswer { cordaProducer }.whenever(cordaProducerBuilder).createProducer(any(), any(), anyOrNull())
        doNothing().whenever(cordaConsumer).assign(any<Collection<CordaTopicPartition>>())
        whenever(cordaConsumer.getPartitions(any())).thenReturn(emptyList())

        doAnswer { cordaConsumer }.whenever(cordaConsumerBuilder).createConsumer(
            any(),
            any(),
            eq(String::class.java),
            eq(RPCResponse::class.java),
            any(),
            anyOrNull(),
        )

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            futureTracker,
            lifecycleCoordinatorFactory
        )
        cordaSenderImpl.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { cordaSenderImpl.isRunning }

        verify(cordaProducerBuilder).createProducer(any(), eq(config.messageBusConfig), anyOrNull())
        verify(cordaConsumerBuilder).createConsumer(
            any(),
            any(),
            eq(String::class.java),
            eq(RPCResponse::class.java),
            any(),
            anyOrNull()
        )

        cordaSenderImpl.close()
        verify(cordaProducer, times(1)).close()
        verify(futureTracker, times(1)).close()
        assertThat(lifeCycleCoordinatorMockHelper.lifecycleCoordinatorThrows).isFalse()
    }

    @Test
    fun `send returns the correct reply`() {
        doAnswer { cordaProducer }.whenever(cordaProducerBuilder).createProducer(any(), any(), anyOrNull())
        doNothing().whenever(cordaConsumer).assign(any<Collection<CordaTopicPartition>>())
        whenever(cordaConsumer.getPartitions(any())).thenReturn(listOf(CordaTopicPartition("", 1)))

        doAnswer { cordaConsumer }.whenever(cordaConsumerBuilder).createConsumer(
            any(),
            any(),
            eq(String::class.java),
            eq(RPCResponse::class.java),
            any(),
            anyOrNull(),
        )

        val sentRecords = argumentCaptor<List<CordaProducerRecord<*, *>>>()
        doNothing().whenever(cordaProducer).sendRecords(sentRecords.capture())

        val correctData = byteArrayOf(8)
        val expectedReply = "Yep"
        val deserializer = mock<CordaAvroDeserializer<String>> {
            on { deserialize(correctData) } doReturn expectedReply
        }
        val serializer = mock<CordaAvroSerializer<String>> {
            on { serialize(any()) } doReturn byteArrayOf(1, 2, 3)
        }

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            futureTracker,
            lifecycleCoordinatorFactory
        )
        var future: CompletableFuture<String>? = null
        whenever(cordaConsumer.poll(any()))
            .thenAnswer {
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
            }
            .thenThrow(CordaRuntimeException("Stop"))

        cordaSenderImpl.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { cordaSenderImpl.isRunning }

        assertThat(future)
            .isNotNull
            .isCompletedWithValue(expectedReply)

        assertThat(lifeCycleCoordinatorMockHelper.lifecycleCoordinatorThrows).isFalse()
    }

    @Test
    fun `test CordaRPCSenderImpl receives intermittent exception and correctly continues`() {
        var firstCall = true
        doAnswer {
            if (firstCall) {
                firstCall = false
                throw CordaMessageAPIIntermittentException("")
            }
            cordaProducer
        }.whenever(cordaProducerBuilder).createProducer(any(), any(), anyOrNull())

        doThrow(CordaMessageAPIFatalException("bail out here")).whenever(cordaConsumerBuilder)
            .createConsumer<Any, Any>(any(), any(), any(), any(), any(), any())

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            futureTracker,
            lifecycleCoordinatorFactory
        )
        cordaSenderImpl.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { cordaSenderImpl.isRunning }
        assertThat(firstCall).isFalse()

        verify(cordaProducerBuilder, times(2)).createProducer(any(), any(), anyOrNull())
        verify(cordaConsumerBuilder, times(1)).createConsumer<Any, Any>(any(), any(), any(), any(), any(), anyOrNull())
        assertThat(lifeCycleCoordinatorMockHelper.lifecycleCoordinatorThrows).isFalse()
    }

    @Test
    fun `test CordaRPCSenderImpl receives auth exception and retries 3 times before failure`() {
        doAnswer { cordaProducer }.whenever(cordaProducerBuilder).createProducer(any(), any(), anyOrNull())
        whenever(cordaConsumer.getPartitions(any())).thenReturn(listOf(CordaTopicPartition("", 1)))
        doAnswer { cordaConsumer }.whenever(cordaConsumerBuilder).createConsumer(
            any(),
            any(),
            eq(String::class.java),
            eq(RPCResponse::class.java),
            any(),
            anyOrNull(),
        )

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            futureTracker,
            lifecycleCoordinatorFactory
        )
        whenever(cordaConsumer.poll(any())).doThrow(CordaMessageAPIAuthException("exception happened"))
        cordaSenderImpl.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { cordaSenderImpl.isRunning }

        verify(cordaProducerBuilder, times(3)).createProducer(any(), any(), anyOrNull())
        verify(cordaConsumerBuilder, times(3)).createConsumer<Any, Any>(any(), any(), any(), any(), any(), anyOrNull())
        verify(cordaConsumer, times(3)).poll(any())

        assertThat(lifeCycleCoordinatorMockHelper.lifecycleCoordinatorThrows).isFalse()
    }

    @Test
    fun `test CordaRPCSenderImpl looper stops thrown Throwables reaching the thread default handler`() {
        val lock = ReentrantLock()
        var subscriptionThread: Thread? = null
        var uncaughtExceptionInSubscriptionThread: Throwable? = null
        doAnswer {
            lock.withLock {
                subscriptionThread = Thread.currentThread()
            }
            // Here's our chance to make sure there are no uncaught exceptions in this, the subscription thread
            subscriptionThread!!.setUncaughtExceptionHandler { _, e ->
                lock.withLock {
                    uncaughtExceptionInSubscriptionThread = e
                }
            }
            cordaProducer
        }.whenever(cordaProducerBuilder).createProducer(any(), any(), anyOrNull())

        doAnswer {
            @Suppress("TooGenericExceptionThrown")
            throw Throwable()
        }.whenever(cordaConsumerBuilder)
            .createConsumer<Any, Any>(any(), any(), any(), any(), any(), any())

        val cordaSenderImpl = CordaRPCSenderImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            serializer,
            deserializer,
            futureTracker,
            lifecycleCoordinatorFactory
        )

        cordaSenderImpl.start()
        // We must wait for the callback above in order we know what thread to join below
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { lock.withLock { subscriptionThread == null } }
        subscriptionThread!!.join(TEST_TIMEOUT_SECONDS * 1000)
        assertThat(lock.withLock { uncaughtExceptionInSubscriptionThread }).isNull()
    }
}
