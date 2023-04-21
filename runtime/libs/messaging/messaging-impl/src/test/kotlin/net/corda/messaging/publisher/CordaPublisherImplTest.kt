package net.corda.messaging.publisher

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaMessageAPIProducerRequiresReset
import net.corda.messaging.api.records.Record
import net.corda.messaging.config.ResolvedPublisherConfig
import net.corda.messaging.utils.toCordaProducerRecord
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException

class CordaPublisherImplTest {
    private lateinit var publisherConfig: ResolvedPublisherConfig
    private lateinit var cordaPublisherImpl: CordaPublisherImpl
    private var producerBuilder = mock<CordaProducerBuilder>()
    private var producer = mock<CordaProducer>()
    private var producerConfig = ProducerConfig("", 0, false, ProducerRoles.PUBLISHER)
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
        publisherConfig = ResolvedPublisherConfig(
            "clientId1",
            1,
            true,
            Duration.ofMillis(100L),
            SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
        )
        producer = mock()
        whenever(producerBuilder.createProducer(any(), any())).thenReturn(producer)
    }

    @Test
    fun testPublish() {
        publish(false, listOf(record, record, record))
        verify(producerBuilder, times(1)).createProducer(any(), any())
        verify(producer, times(3)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Captor
    val callbackCaptor: ArgumentCaptor<CordaProducer.Callback> = ArgumentCaptor.forClass(CordaProducer.Callback::class.java)

    @Test
    fun testPublishFatalError() {
        doAnswer {
            callbackCaptor.firstValue.onCompletion(CordaMessageAPIFatalException("", IllegalStateException()))
        }.whenever(producer).send(any(), callbackCaptor.capture())
        val futures = publish(false, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishToPartitionFatalError() {
        doAnswer {
            callbackCaptor.firstValue.onCompletion(CordaMessageAPIFatalException("", IllegalStateException()))
        }.whenever(producer).send(any(), eq(1), callbackCaptor.capture())
        val futures = publishToPartition(false, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishIntermittentError() {
        doAnswer {
            callbackCaptor.firstValue.onCompletion(CordaMessageAPIIntermittentException("", IllegalStateException()))
        }.whenever(producer).send(any(), callbackCaptor.capture())
        val futures = publish(false, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testPublishToPartitionIntermittentError() {
        doAnswer {
            callbackCaptor.firstValue.onCompletion(CordaMessageAPIIntermittentException("", IllegalStateException()))
        }.whenever(producer).send(any(), eq(1), callbackCaptor.capture())
        val futures = publishToPartition(false, listOf(1 to record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
    }

    @Test
    fun testTransactionPublish() {
        publish(true, listOf(record, record, record))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionPublishFromMultipleThreads() {
        val barrier = CyclicBarrier(3)
        val thread1 = Thread {
            barrier.await()
            publish(true, listOf(record, record, record))
            barrier.await()
        }
        val thread2 = Thread {
            barrier.await()
            publish(true, listOf(record, record, record))
            barrier.await()
        }
        thread1.start()
        thread2.start()
        barrier.await()
        barrier.await()
        verify(producer, times(2)).sendRecords(any())
        verify(producer, times(2)).beginTransaction()
        verify(producer, times(2)).commitTransaction()
    }

    @Test
    fun testTransactionPublishToPartition() {
        val recordsWithPartitions = listOf(1 to record, 2 to record, 3 to record)
        val cordaProducerRecordsWithPartitions = recordsWithPartitions.map {
            Pair(it.first, it.second.toCordaProducerRecord())
        }
        publishToPartition(true, recordsWithPartitions)
        verify(producer, times(1)).sendRecordsToPartitions(cordaProducerRecordsWithPartitions)
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionFailure() {
        doThrow(CordaMessageAPIFatalException("")).whenever(producer).beginTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionBeginTransactionFailureForPublishToPartition() {
        doThrow(IllegalStateException("")).whenever(producer).beginTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(0)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(0)).commitTransaction()
    }

    @Test
    fun testTransactionCommitIntermittentFailure() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
        // Intermittent failures will retry once
        verify(producer, times(2)).sendRecords(any())
        verify(producer, times(2)).beginTransaction()
        verify(producer, times(2)).commitTransaction()
    }

    @Test
    fun testTransactionCommitIntermittentFailureForPublishToPartition() {
        doThrow(CordaMessageAPIIntermittentException("")).whenever(producer).commitTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
        // Intermittent failures will retry once
        verify(producer, times(2)).sendRecordsToPartitions(any())
        verify(producer, times(2)).beginTransaction()
        verify(producer, times(2)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFatalFailure() {
        doThrow(CordaMessageAPIFatalException("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitFatalFailureForPublishToPartition() {
        doThrow(CordaMessageAPIFatalException("")).whenever(producer).commitTransaction()
        val futures = publishToPartition(true, listOf(1 to record))
        assertThrows(CordaMessageAPIFatalException::class.java, getCauseOrThrow(futures[0]))
        verify(producer, times(1)).sendRecordsToPartitions(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTransactionCommitIntermittentFailureRequiresNewPublisher() {
        doThrow(CordaMessageAPIProducerRequiresReset("")).whenever(producer).commitTransaction()
        val futures = publish(true, listOf(record))
        assertThrows(CordaMessageAPIIntermittentException::class.java, getCauseOrThrow(futures[0]))
        // Intermittent failures will retry once
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
        verify(producer, times(1)).close()
        verify(producerBuilder, times(2)).createProducer(any(), any())
    }

    @Test
    fun testSafeClose() {
        cordaPublisherImpl = CordaPublisherImpl(publisherConfig, producerConfig, producerBuilder)

        cordaPublisherImpl.close()
        verify(producer, times(1)).close()
    }

    private fun publish(isTransaction: Boolean = false, records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        cordaPublisherImpl = createPublisher(isTransaction)

        return cordaPublisherImpl.publish(records)
    }

    private fun publishToPartition(
        isTransaction: Boolean = false,
        recordsWithPartitions: List<Pair<Int, Record<*, *>>>
    ): List<CompletableFuture<Unit>> {
        cordaPublisherImpl = createPublisher(isTransaction)

        return cordaPublisherImpl.publishToPartition(recordsWithPartitions)
    }

    private fun createPublisher(transactional: Boolean): CordaPublisherImpl {
        val publisherConfig = ResolvedPublisherConfig(
            "clientId1",
            1,
            transactional,
            Duration.ofMillis(100L),
            SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
        )
        return CordaPublisherImpl(publisherConfig, producerConfig, producerBuilder)
    }
}
