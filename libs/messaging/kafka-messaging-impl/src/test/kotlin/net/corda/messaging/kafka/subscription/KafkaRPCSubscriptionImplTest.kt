package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.data.messaging.RPCRequest
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RPC_RESPONDER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.subscription.CordaAvroDeserializer
import net.corda.messaging.kafka.subscription.KafkaRPCSubscriptionImpl
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.p2p.app.HoldingIdentity
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


class KafkaRPCSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 2L
    }

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_RPC_RESPONDER)

    private val dummyRequest = HoldingIdentity(
        "identity",
        "group"
    )
    private val requestRecord = listOf<ConsumerRecordAndMeta<String, RPCRequest>>(
        ConsumerRecordAndMeta(
            TOPIC_PREFIX,
            ConsumerRecord(
                TOPIC,
                0,
                0,
                "0",
                RPCRequest(
                    "0",
                    Instant.now().toEpochMilli(),
                    "$TOPIC.resp",
                    0,
                    dummyRequest.toByteBuffer()
                )
            )
        )
    )

    private val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
        whenever(it.deserialize(any(), any(), anyOrNull())).thenReturn(dummyRequest)
        whenever(it.getClassType(any())).thenReturn(HoldingIdentity::class.java)
    }
    private val deserializer = CordaAvroDeserializer(schemaRegistry, mock(), HoldingIdentity::class.java)

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `rpc subscription returns correct results`() {
        val processor = TestProcessor()
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks()

        doAnswer {
            requestRecord
        }.whenever(kafkaConsumer).poll()

        doAnswer {
            listOf(TopicPartition(TOPIC, 0))
        }.whenever(kafkaConsumer).getPartitions(any(), any())

        val subscription = KafkaRPCSubscriptionImpl(
            config,
            mock(),
            consumerBuilder,
            processor,
            CordaAvroSerializer(schemaRegistry),
            deserializer
        )
        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(kafkaConsumer, times(1)).subscribe(listOf("topic"))
        Assertions.assertThat(processor.incomingRecords.size).isEqualTo(1)
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

    private class TestProcessor : RPCResponderProcessor<HoldingIdentity, HoldingIdentity> {
        val log = contextLogger()

        var failNext = false
        val incomingRecords = mutableListOf<HoldingIdentity>()


        override fun onNext(request: HoldingIdentity, respFuture: CompletableFuture<HoldingIdentity>) {
            log.info("Processing new request")
            if (failNext) {
                throw CordaMessageAPIFatalException("Abandon Ship!")
            }
            incomingRecords += request
            failNext = true
        }
    }

}