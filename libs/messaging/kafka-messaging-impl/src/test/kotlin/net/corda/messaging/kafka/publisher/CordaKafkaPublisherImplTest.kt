package net.corda.messaging.kafka.publisher

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.producer.wrapper.impl.CordaKafkaProducerImpl
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_CLIENT_ID
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.apache.kafka.common.errors.ProducerFencedException
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
    private lateinit var cordaKafkaPublisherImpl : CordaKafkaPublisherImpl
    private lateinit var kafkaConfig: Config
    private lateinit var producer : CordaKafkaProducer
    private lateinit var mockProducer: MockProducer<String, ByteBuffer>
    private val record = Record("topic", "key1", ByteBuffer.wrap("value1".toByteArray()))

    @BeforeEach
    fun beforeEach() {
        producer = mock()
        publisherConfig  = PublisherConfig("clientId", )
        kafkaConfig = ConfigFactory.empty()
            .withValue(PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
            .withValue(KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))
            .withValue(PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef("clientId1"))
    }

    @Test
    fun testPublish() {
        publish(false, listOf(record, record, record))
        verify(producer, times(3)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).tryCommitTransaction()
    }

    @Test
    fun testPublishFatalError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig, uncheckedCast(mockProducer))
        val future = publish(false, listOf(record))
        mockProducer.errorNext(IllegalStateException(""))

        assertThrows(CordaMessageAPIFatalException::class.java) { future[0].getOrThrow() }
    }

    @Test
    fun testPublishIntermittentError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig, uncheckedCast(mockProducer))
        val future = publish(false, listOf(record))
        mockProducer.errorNext(InterruptException(""))

        assertThrows(CordaMessageAPIIntermittentException::class.java) { future[0].getOrThrow() }
    }


    @Test
    fun testPublishUnknownError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig, uncheckedCast(mockProducer))
        val future = publish(false, listOf(record))
        mockProducer.errorNext(IllegalArgumentException(""))

        assertThrows(CordaMessageAPIFatalException::class.java) { future[0].getOrThrow() }
    }

    @Test
    fun testTransactionPublish() {
        publish(true, listOf(record, record, record))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).tryCommitTransaction()
    }


    @Test
    fun testTransactionBeginTransactionFailureIllegalStateException() {
        doThrow(IllegalStateException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).tryCommitTransaction()
    }


    @Test
    fun testTransactionBeginTransactionAuthorizationException() {
        doThrow(AuthorizationException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).tryCommitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionProducerFencedException() {
        doThrow(ProducerFencedException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).tryCommitTransaction()
    }

    @Test
    fun testTransactionCommitFailureTimeout() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).tryCommitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).tryCommitTransaction()
    }

    @Test
    fun testTransactionCommitFailureEpochException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).tryCommitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).tryCommitTransaction()
    }

    @Test
    fun testTransactionCommitFailureInterruptException() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).tryCommitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).tryCommitTransaction()
    }

    @Test
    fun testTransactionCommitFailureCordaMessageAPIFatalException() {
        doThrow(CordaMessageAPIFatalException("")).whenever(producer).tryCommitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).tryCommitTransaction()
    }

    @Test
    fun testTransactionCommitFailureUnknownException() {
        doThrow(IllegalArgumentException("")).whenever(producer).tryCommitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java) { futures[0].getOrThrow() }
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).tryCommitTransaction()
    }

    @Test
    fun testSafeClose() {
        cordaKafkaPublisherImpl = CordaKafkaPublisherImpl(publisherConfig, kafkaConfig, producer)

        cordaKafkaPublisherImpl.close()
        verify(producer, times(1)).close(Mockito.any(Duration::class.java))
    }

    private fun publish(isTransaction: Boolean = false, records: List<Record<String, ByteBuffer>>) : List<CordaFuture<Boolean>> {
        publisherConfig = if (isTransaction) {
            PublisherConfig("clientId", 1)
        } else {
            PublisherConfig("clientId", )
        }
        cordaKafkaPublisherImpl = CordaKafkaPublisherImpl(publisherConfig, kafkaConfig, producer,)

        return cordaKafkaPublisherImpl.publish(records)
    }
}
