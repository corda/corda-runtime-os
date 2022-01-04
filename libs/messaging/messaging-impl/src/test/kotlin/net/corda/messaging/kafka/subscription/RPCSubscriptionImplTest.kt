package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.identity.HoldingIdentity
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RPC_RESPONDER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.subscription.RPCSubscriptionImpl
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture


class RPCSubscriptionImplTest {

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_RPC_RESPONDER)
    private val dummyRequest = HoldingIdentity(
        "identity",
        "group"
    )
    private val requestRecord = listOf(
        CordaConsumerRecord(
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
            ),
            0
        )
    )
    private val deserializer: CordaAvroDeserializer<HoldingIdentity> = mock<CordaAvroDeserializer<HoldingIdentity>>().also {
        whenever(it.deserialize(any())).thenReturn(dummyRequest)
    }
    private val serializer: CordaAvroSerializer<HoldingIdentity> = mock<CordaAvroSerializer<HoldingIdentity>>().also {
        whenever(it.serialize(any())).thenReturn(dummyRequest.toByteBuffer().array())
    }
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private lateinit var kafkaConsumer: CordaConsumer<String, RPCRequest>
    private lateinit var cordaConsumerBuilder: CordaConsumerBuilder
    private val kafkaProducer: CordaProducer = mock()
    private val cordaProducerBuilder: CordaProducerBuilder = mock()

    @Captor
    private val captor = argumentCaptor<List<Pair<Int, CordaProducerRecord<Int, RPCResponse>>>>()

    @BeforeEach
    fun before() {
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks()
        this.kafkaConsumer = kafkaConsumer
        this.cordaConsumerBuilder = consumerBuilder

        doAnswer { kafkaProducer }.whenever(cordaProducerBuilder).createProducer(any())

        doAnswer {
            requestRecord
        }.whenever(kafkaConsumer).poll()

        doAnswer {
            listOf(CordaTopicPartition(TOPIC, 0))
        }.whenever(kafkaConsumer).getPartitions(any(), any())

        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    @Test
    fun `rpc subscription receives request and completes it OK`() {
        val processor = TestProcessor(ResponseStatus.OK)
        val subscription = RPCSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(kafkaConsumer, times(1)).subscribe(TOPIC)
        assertThat(processor.incomingRecords.size).isEqualTo(1)
        verify(kafkaProducer, times(1)).sendRecordsToPartitions(captor.capture())
        val capturedValue = captor.firstValue
        assertEquals(capturedValue[0].second.value?.responseStatus, ResponseStatus.OK)
    }

    @Test
    fun `rpc subscription receives request and completes it exceptionally`() {
        val processor = TestProcessor(ResponseStatus.FAILED)
        val subscription = RPCSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(kafkaConsumer, times(1)).subscribe(TOPIC)
        assertThat(processor.incomingRecords.size).isEqualTo(1)
        verify(kafkaProducer, times(1)).sendRecordsToPartitions(captor.capture())
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
        val subscription = RPCSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(kafkaConsumer, times(1)).subscribe(TOPIC)
        assertThat(processor.incomingRecords.size).isEqualTo(1)
        verify(kafkaProducer, times(1)).sendRecordsToPartitions(captor.capture())
        val capturedValue = captor.firstValue
        assertEquals(capturedValue[0].second.value?.responseStatus, ResponseStatus.CANCELLED)
    }

    private fun setupStandardMocks(): Pair<CordaConsumer<String, RPCRequest>, CordaConsumerBuilder> {
        val kafkaConsumer: CordaConsumer<String, RPCRequest> = mock()
        val cordaConsumerBuilder: CordaConsumerBuilder = mock()
        doReturn(kafkaConsumer).whenever(cordaConsumerBuilder).createRPCConsumer<String, RPCRequest>(any(), any(), any(), any())
        doReturn(
            mutableMapOf(
                CordaTopicPartition(TOPIC, 0) to 0L,
                CordaTopicPartition(TOPIC, 1) to 0L
            )
        ).whenever(kafkaConsumer)
            .beginningOffsets(any())
        return Pair(kafkaConsumer, cordaConsumerBuilder)
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
