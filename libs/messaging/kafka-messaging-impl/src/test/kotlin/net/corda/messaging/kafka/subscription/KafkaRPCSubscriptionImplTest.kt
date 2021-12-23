package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.data.ExceptionEnvelope
import net.corda.data.identity.HoldingIdentity
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RPC_RESPONDER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture


class KafkaRPCSubscriptionImplTest {

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_RPC_RESPONDER)
    private val dummyRequest = HoldingIdentity(
        "identity",
        "group"
    )
    private val requestRecord = listOf<ConsumerRecord<String, RPCRequest>>(
        ConsumerRecord(
            TOPIC_PREFIX + TOPIC,
            0,
            0,
            "0",
            RPCRequest(
                "0",
                Instant.now().toEpochMilli(),
                "$TOPIC_PREFIX$TOPIC.resp",
                0,
                dummyRequest.toByteBuffer()
            )
        )
    )
    private val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
        whenever(it.deserialize(any(), any(), anyOrNull())).thenReturn(dummyRequest)
        whenever(it.serialize(any())).thenReturn(dummyRequest.toByteBuffer())
        whenever(it.getClassType(any())).thenReturn(HoldingIdentity::class.java)
    }
    private val deserializer = CordaAvroDeserializer(schemaRegistry, mock(), HoldingIdentity::class.java)
    private val serializer = CordaAvroSerializer<HoldingIdentity>(schemaRegistry)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private lateinit var kafkaConsumer: CordaKafkaConsumer<String, RPCRequest>
    private lateinit var consumerBuilder: ConsumerBuilder<String, RPCRequest>
    private val producerBuilder: ProducerBuilder = mock()
    private val producer: CordaKafkaProducer = mock()

    @Captor
    private val captor = argumentCaptor<List<Pair<Int, Record<Int, RPCResponse>>>>()

    @BeforeEach
    fun before() {
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks()
        this.kafkaConsumer = kafkaConsumer
        this.consumerBuilder = consumerBuilder

        doAnswer { producer }.whenever(producerBuilder).createProducer(any())

        doAnswer {
            requestRecord
        }.whenever(kafkaConsumer).poll()

        doAnswer {
            listOf(TopicPartition(TOPIC, 0))
        }.whenever(kafkaConsumer).getPartitions(any(), any())

        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    @Test
    fun `rpc subscription receives request and completes it OK`() {
        val processor = TestProcessor(ResponseStatus.OK)
        val subscription = KafkaRPCSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(kafkaConsumer, times(1)).subscribeToTopic()
        assertThat(processor.incomingRecords.size).isEqualTo(1)
        verify(producer, times(1)).sendRecordsToPartitions(captor.capture())
        val capturedValue = captor.firstValue
        assertEquals(capturedValue[0].second.value?.responseStatus, ResponseStatus.OK)
    }

    @Test
    fun `rpc subscription receives request and completes it exceptionally`() {
        val processor = TestProcessor(ResponseStatus.FAILED)
        val subscription = KafkaRPCSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(kafkaConsumer, times(1)).subscribeToTopic()
        assertThat(processor.incomingRecords.size).isEqualTo(1)
        verify(producer, times(1)).sendRecordsToPartitions(captor.capture())
        val capturedValue = captor.firstValue
        assertEquals(capturedValue[0].second.value?.responseStatus, ResponseStatus.FAILED)
        assertEquals(
            ExceptionEnvelope.fromByteBuffer(capturedValue[0].second.value?.payload),
            ExceptionEnvelope(CordaMessageAPIFatalException::class.java.name, "Abandon ship")
        )
    }

    @Test
    fun `rpc subscription receives request and cancels it`() {
        val processor = TestProcessor(ResponseStatus.CANCELLED)
        val subscription = KafkaRPCSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(kafkaConsumer, times(1)).subscribeToTopic()
        assertThat(processor.incomingRecords.size).isEqualTo(1)
        verify(producer, times(1)).sendRecordsToPartitions(captor.capture())
        val capturedValue = captor.firstValue
        assertEquals(capturedValue[0].second.value?.responseStatus, ResponseStatus.CANCELLED)
    }

    private fun setupStandardMocks(): Pair<CordaKafkaConsumer<String, RPCRequest>, ConsumerBuilder<String, RPCRequest>> {
        val kafkaConsumer: CordaKafkaConsumer<String, RPCRequest> = mock()
        val consumerBuilder: ConsumerBuilder<String, RPCRequest> = mock()
        doReturn(kafkaConsumer).whenever(consumerBuilder).createRPCConsumer(any(), any(), any(), any())
        doReturn(
            mutableMapOf(
                TopicPartition(TOPIC, 0) to 0L,
                TopicPartition(TOPIC, 1) to 0L
            )
        ).whenever(kafkaConsumer)
            .beginningOffsets(any())
        return Pair(kafkaConsumer, consumerBuilder)
    }

    private class TestProcessor(val responseStatus: ResponseStatus) :
        RPCResponderProcessor<HoldingIdentity, HoldingIdentity> {
        val log = contextLogger()

        var failNext = false
        val incomingRecords = mutableListOf<HoldingIdentity>()

        override fun onNext(request: HoldingIdentity, respFuture: CompletableFuture<HoldingIdentity>) {
            log.info("Processing new request")
            if (failNext) {
                throw CordaMessageAPIFatalException("Abandon Ship!")
            } else {
                when (responseStatus) {
                    ResponseStatus.OK -> {
                        respFuture.complete(
                            HoldingIdentity("identity", "group")
                        )
                    }
                    ResponseStatus.FAILED -> {
                        respFuture.completeExceptionally(CordaMessageAPIFatalException("Abandon ship"))
                    }
                    else -> {
                        respFuture.cancel(true)
                    }
                }
            }
            incomingRecords += request
            failNext = true
        }
    }

}
