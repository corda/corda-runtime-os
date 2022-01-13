package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX_PATH
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.producer.wrapper.impl.CordaKafkaProducerImpl
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.GROUP_INSTANCE_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_PRODUCER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_PUBLISHER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_CLIENT_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.v5.base.util.uncheckedCast
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
import org.junit.jupiter.api.function.Executable
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class CordaKafkaPublisherImplTest {
    private lateinit var publisherConfig: PublisherConfig
    private lateinit var cordaKafkaPublisherImpl: CordaKafkaPublisherImpl
    private lateinit var kafkaConfig: Config
    private lateinit var producer: CordaKafkaProducer
    private lateinit var mockProducer: MockProducer<String, ByteBuffer>
    private val record = Record("topic", "key1", ByteBuffer.wrap("value1".toByteArray()))

    @Throws(IllegalStateException::class)
    private fun <T : Any?> getCauseOrThrow(completableFuture: CompletableFuture<T>): Executable {
        return Executable {
            try {
                throw IllegalStateException("Unexpected ${completableFuture.get()}!")
            } catch (e: ExecutionException) {
                throw e.cause!!
            }
        }
    }

    @BeforeEach
    fun beforeEach() {
        producer = mock()
        publisherConfig = PublisherConfig("clientId")
        kafkaConfig = createStandardTestConfig().getConfig(PATTERN_PUBLISHER)
            .withValue(PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
            .withValue(TOPIC_PREFIX_PATH, ConfigValueFactory.fromAnyRef("prefix"))
            .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("clientId1"))
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
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig.getConfig(KAFKA_PRODUCER), uncheckedCast(mockProducer))
        val futures = publish(false, listOf(record))
        mockProducer.errorNext(IllegalStateException(""))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishToPartitionFatalError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig.getConfig(KAFKA_PRODUCER), uncheckedCast(mockProducer))
        val futures = publishToPartition(false, listOf(1 to record))
        mockProducer.errorNext(IllegalStateException(""))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishIntermittentError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig.getConfig(KAFKA_PRODUCER), uncheckedCast(mockProducer))
        val futures = publish(false, listOf(record))
        mockProducer.errorNext(InterruptException(""))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishToPartitionIntermittentError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig.getConfig(KAFKA_PRODUCER), uncheckedCast(mockProducer))
        val futures = publishToPartition(false, listOf(1 to record))
        mockProducer.errorNext(InterruptException(""))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishUnknownError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig.getConfig(KAFKA_PRODUCER), uncheckedCast(mockProducer))
        val futures = publish(false, listOf(record))
        mockProducer.errorNext(IllegalArgumentException(""))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishToPartitionUnknownError() {
        mockProducer = MockProducer(false, StringSerializer(), ByteBufferSerializer())
        producer = CordaKafkaProducerImpl(kafkaConfig.getConfig(KAFKA_PRODUCER), uncheckedCast(mockProducer))
        val futures = publish(false, listOf(record))
        mockProducer.errorNext(IllegalArgumentException(""))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testTransactionPublish() {
        publish(true, listOf(record, record, record))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionPublishToPartition() {
        val recordsWithPartitions = listOf(1 to record, 2 to record, 3 to record)
        publishToPartition(true, recordsWithPartitions)
        verify(producer, times(1)).sendRecordsToPartitions(recordsWithPartitions)
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionFailureIllegalStateException() {
        doThrow(IllegalStateException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionFailureIllegalStateExceptionForPublishToPartition() {
        doThrow(IllegalStateException("")).whenever(producer).beginTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionAuthorizationException() {
        doThrow(AuthorizationException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionAuthorizationExceptionForPublishToPartition() {
        doThrow(AuthorizationException("")).whenever(producer).beginTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionProducerFencedException() {
        doThrow(ProducerFencedException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionProducerFencedExceptionForPublishToPartition() {
        doThrow(ProducerFencedException("")).whenever(producer).beginTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureTimeout() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureTimeoutForPublishToPartition() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).commitTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecordsToPartitions(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureEpochException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureEpochExceptionForPublishToPartition() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).commitTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecordsToPartitions(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureInterruptException() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureInterruptExceptionForPublishToPartition() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).commitTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecordsToPartitions(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureCordaMessageAPIFatalException() {
        doThrow(CordaMessageAPIFatalException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureCordaMessageAPIFatalExceptionForPublishToPartition() {
        doThrow(CordaMessageAPIFatalException("")).whenever(producer).commitTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecordsToPartitions(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureUnknownException() {
        doThrow(IllegalArgumentException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFailureUnknownExceptionForPublishToPartition() {
        doThrow(IllegalArgumentException("")).whenever(producer).commitTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecordsToPartitions(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testSafeClose() {
        cordaKafkaPublisherImpl = CordaKafkaPublisherImpl(kafkaConfig, producer)

        cordaKafkaPublisherImpl.close()
        verify(producer, times(1)).close(any())
    }

    private fun publish(isTransaction: Boolean = false, records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        val publisherConfig = if (isTransaction) {
            kafkaConfig
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))
                .withValue(GROUP_INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        } else {
            kafkaConfig
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))
                .withoutPath(PRODUCER_TRANSACTIONAL_ID)
        }
        cordaKafkaPublisherImpl = CordaKafkaPublisherImpl(publisherConfig, producer)

        return cordaKafkaPublisherImpl.publish(records)
    }

    private fun publishToPartition(
        isTransaction: Boolean = false,
        recordsWithPartitions: List<Pair<Int, Record<*, *>>>
    ): List<CompletableFuture<Unit>> {
        val publisherConfig = if (isTransaction) {
            kafkaConfig
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))
                .withValue(GROUP_INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        } else {
            kafkaConfig
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef(publisherConfig.clientId))
                .withoutPath(PRODUCER_TRANSACTIONAL_ID)
        }
        cordaKafkaPublisherImpl = CordaKafkaPublisherImpl(publisherConfig, producer)

        return cordaKafkaPublisherImpl.publishToPartition(recordsWithPartitions)
    }
}
