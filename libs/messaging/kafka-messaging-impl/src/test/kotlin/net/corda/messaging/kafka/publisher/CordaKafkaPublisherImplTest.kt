package net.corda.messaging.kafka.publisher

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.concurrent.CordaFuture
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.serialization.ByteBufferSerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.ByteBuffer
import java.time.Duration

class CordaKafkaPublisherImplTest {
    private lateinit var publisherConfig : PublisherConfig
    private lateinit var cordaKafkaPublisherImpl : CordaKafkaPublisherImpl<String, ByteBuffer>
    private lateinit var kafkaConfig: Config
    private lateinit var producer : MockProducer<String, ByteBuffer>
    private val avroSchemaRegistry : AvroSchemaRegistry = mock()
    private val record = Record("topic", "key1", ByteBuffer.wrap("value1".toByteArray()))

    @BeforeEach
    fun beforeEach() {
        producer = mock()
        publisherConfig  = PublisherConfig("clientId")
        kafkaConfig = ConfigFactory.empty().withValue(PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
        kafkaConfig = kafkaConfig.withValue(KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))
    }

    @Test
    fun testPublish() {
        publish(false, listOf(record, record, record))
        verify(producer, times(3)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testPublishFatalError() {
        producer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        val future = publish(false, listOf(record))
        producer.errorNext(IllegalStateException(""))

        assertThrows(CordaMessageAPIFatalException::class.java) { future[0].getOrThrow() }
    }

    @Test
    fun testPublishIntermittentError() {
        producer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        val future = publish(false, listOf(record))
        producer.errorNext(InterruptException(""))
        assertThrows(CordaMessageAPIIntermittentException::class.java) { future[0].getOrThrow() }
    }

    @Test
    fun testPublishUnknownError() {
        producer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        val future = publish(false, listOf(record))
        producer.errorNext(IllegalArgumentException())

        assertThrows(CordaMessageAPIFatalException::class.java) { future[0].getOrThrow() }
    }

    @Test
    fun testTransactionPublish() {
        publish(true, listOf(record, record, record))
        verify(producer, times(3)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }


    @Test
    fun testTransactionBeginTransactionFailureIllegalStateException() {
        doThrow(IllegalStateException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(0)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }


    @Test
    fun testTransactionBeginTransactionAuthorizationException() {
        doThrow(AuthorizationException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(0)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionProducerFencedException() {
        doThrow(ProducerFencedException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(0)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureTimeout() {
        doThrow(TimeoutException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureEpochException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureInterruptException() {
        doThrow(InterruptException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureKafkaException() {
        doThrow(ProducerFencedException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureUnknownException() {
        doThrow(IllegalArgumentException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).send(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testSafeClose() {
        cordaKafkaPublisherImpl = CordaKafkaPublisherImpl(publisherConfig, kafkaConfig, producer, avroSchemaRegistry)

        cordaKafkaPublisherImpl.close()
        verify(producer, times(1)).close(Mockito.any(Duration::class.java))
    }

    private fun publish(isTransaction: Boolean = false, records: List<Record<String, ByteBuffer>>) : List<CordaFuture<Unit>> {
        publisherConfig = if (isTransaction) {
            PublisherConfig("clientId", 1)
        } else {
            PublisherConfig("clientId")
        }
        cordaKafkaPublisherImpl = CordaKafkaPublisherImpl(publisherConfig, kafkaConfig, producer, avroSchemaRegistry)

        return cordaKafkaPublisherImpl.publish(records)
    }
}
