package net.corda.messaging.kafka.publisher

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.v5.base.concurrent.CordaFuture
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.time.Duration

class CordaKafkaPublisherTest {
    private lateinit var publisherConfig : PublisherConfig
    private lateinit var cordaKafkaPublisher : CordaKafkaPublisher<String, ByteArray>
    private lateinit var kafkaConfig: Config
    private val producer : MockProducer<String, ByteArray> = mock()

    @BeforeEach
    fun beforeEach() {
        publisherConfig  = PublisherConfig("clientId", "topic")
        kafkaConfig = ConfigFactory.empty().withValue(PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
        kafkaConfig = kafkaConfig.withValue(KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))
    }

    @Test
    fun testPublish() {
        val future = publish()
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testPublishFatalError() {
        doThrow(KafkaException()).whenever(producer).send(any(), any())
        val future = publish()
        assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testPublishIntermittentError() {
        doThrow(InterruptException("")).whenever(producer).send(any(), any())
        val future = publish()
        assertThrows(CordaMessageAPIIntermittentException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testPublishUnknownError() {
        doThrow(IllegalArgumentException("")).whenever(producer).send(any(), any())
        val future = publish()
        assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionPublish() {
        val future = publish(true)
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }


    @Test
    fun testTransactionBeginTransactionFailureIllegalStateException() {
        doThrow(IllegalStateException("")).whenever(producer).beginTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        verify(producer, times(0)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }


    @Test
    fun testTransactionBeginTransactionAuthorizationException() {
        doThrow(AuthorizationException("")).whenever(producer).beginTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        verify(producer, times(0)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionProducerFencedException() {
        doThrow(ProducerFencedException("")).whenever(producer).beginTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        verify(producer, times(0)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureTimeout() {
        doThrow(TimeoutException("")).whenever(producer).commitTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIIntermittentException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureEpochException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).commitTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIIntermittentException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureInterruptException() {
        doThrow(InterruptException("")).whenever(producer).commitTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIIntermittentException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureKafkaException() {
        doThrow(KafkaException("")).whenever(producer).commitTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureUnknownException() {
        doThrow(IllegalArgumentException("")).whenever(producer).commitTransaction()
        val future = publish(true)
        assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testSafeClose() {
        cordaKafkaPublisher = CordaKafkaPublisher(publisherConfig, kafkaConfig, producer)

        cordaKafkaPublisher.close()
        verify(producer, times(1)).close(Mockito.any(Duration::class.java))
    }

    private fun publish(isTransaction: Boolean = false) : CordaFuture<Boolean> {
        publisherConfig = if (isTransaction) {
            PublisherConfig("clientId", "topic", 1)
        } else {
            PublisherConfig("clientId", "topic")
        }
        cordaKafkaPublisher = CordaKafkaPublisher(publisherConfig, kafkaConfig, producer)

        val record = Record("topic", "key1", "value1".toByteArray())
        return cordaKafkaPublisher.publish(record)
    }
}