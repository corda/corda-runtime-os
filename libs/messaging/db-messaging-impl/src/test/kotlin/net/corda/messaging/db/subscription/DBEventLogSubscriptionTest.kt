package net.corda.messaging.db.subscription

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.db.partition.PartitionAllocationListener
import net.corda.messaging.db.partition.PartitionAllocator
import net.corda.messaging.db.partition.PartitionAssignor
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.FetchWindow
import net.corda.messaging.db.persistence.RecordDbEntry
import net.corda.messaging.db.persistence.TransactionResult
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.SQLTransientException
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class DBEventLogSubscriptionTest {

    private val pollingTimeout = 5.millis

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"
    private val topicPartitions = 2
    private val dbRecords = listOf(topic1, topic2).map {
        it to (1..topicPartitions).map { it to Collections.synchronizedList<RecordDbEntry>(mutableListOf()) }.toMap()
    }.toMap()
    private val dbCommittedOffsets = listOf(topic1, topic2)
        .map { it to (1..topicPartitions).map { it to Collections.synchronizedList<Long>(mutableListOf()) }.toMap() }
        .toMap()

    private val offsetsPerTopicPartition = listOf(topic1, topic2).map {
        it to (1..topicPartitions).map { it to AtomicLong(1) }.toMap()
    }.toMap()
    private val releasedOffsetsPerTopicPartition = listOf(topic1, topic2).map {
        it to (1..topicPartitions).map { it to Collections.synchronizedList<Long>(mutableListOf()) }.toMap()
    }.toMap()

    private val processedRecords = Collections.synchronizedList<EventLogRecord<String, String>>(mutableListOf())

    private var failuresToSimulateForDbOffsetWrite = 0
    private var failuresToSimulateForDbRecordsWrite = 0
    private var failuresToSimulateForAtomicDbWrite = 0
    private val dbAccessProvider = mock(DBAccessProvider::class.java).apply {
        @Suppress("UNCHECKED_CAST")
        `when`(writeRecords(anyList(), anyOrNull()))
            .thenAnswer { invocation ->
                val records = (invocation.arguments.first() as List<RecordDbEntry>)
                val postTxFn =
                    invocation.arguments[1] as ((records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)

                var transactionResult: TransactionResult? = null
                try {
                    if (failuresToSimulateForDbRecordsWrite > 0) {
                        transactionResult = TransactionResult.ROLLED_BACK
                        failuresToSimulateForDbRecordsWrite--
                        throw SQLTransientException()
                    }

                    records.forEach { dbRecords[it.topic]!![it.partition]!!.add(it) }
                    transactionResult = TransactionResult.COMMITTED
                } finally {
                    postTxFn(records, transactionResult!!)
                }
            }
        @Suppress("UNCHECKED_CAST")
        `when`(readRecords(anyString(), anyList()))
            .thenAnswer { invocation ->
                val topic = invocation.arguments[0] as String
                val fetchWindows = invocation.arguments[1] as List<FetchWindow>
                fetchWindows.flatMap { window ->
                    dbRecords[topic]!![window.partition]!!
                        .filter { it.offset >= window.startOffset && it.offset <= window.endOffset }
                        .take(window.limit)
                }
            }
        @Suppress("UNCHECKED_CAST")
        `when`(writeOffsets(anyString(), anyString(), anyOrNull()))
            .thenAnswer { invocation ->
                if (failuresToSimulateForDbOffsetWrite > 0) {
                    failuresToSimulateForDbOffsetWrite--
                    throw SQLTransientException()
                }

                val topic = invocation.arguments[0] as String
                val offsets = invocation.arguments[2] as Map<Int, Long>
                offsets.forEach { (partition, offset) -> dbCommittedOffsets[topic]!![partition]!!.add(offset) }
            }
        @Suppress("UNCHECKED_CAST")
        `when`(writeOffsetsAndRecordsAtomically(anyString(), anyString(), anyOrNull(), anyList(), anyOrNull()))
            .thenAnswer { invocation ->
                val topic = invocation.arguments[0] as String
                val consumerGroup = invocation.arguments[1] as String
                val offsets = invocation.arguments[2] as Map<Int, Long>
                val records = invocation.arguments[3] as List<RecordDbEntry>
                val postTxFn =
                    invocation.arguments[4] as ((records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)

                if (failuresToSimulateForAtomicDbWrite > 0) {
                    postTxFn(records, TransactionResult.ROLLED_BACK)
                    failuresToSimulateForAtomicDbWrite--
                    throw SQLTransientException()
                }

                writeOffsets(topic, consumerGroup, offsets)
                writeRecords(records, postTxFn)
            }
        @Suppress("UNCHECKED_CAST")
        `when`(getMaxCommittedOffset(anyString(), anyString(), anyOrNull()))
            .thenAnswer { invocation ->
                val topic = invocation.arguments[0] as String
                val partitions = invocation.arguments[2] as Set<Int>
                partitions.map { it to dbCommittedOffsets[topic]!![it]!!.maxOrNull() }.toMap()
            }
        `when`(getTopics()).thenReturn(
            mapOf(
                topic1 to topicPartitions,
                topic2 to topicPartitions
            )
        )
    }
    private val avroSchemaRegistry = mock(AvroSchemaRegistry::class.java).apply {
        `when`(serialize(anyOrNull())).thenAnswer { invocation ->
            val bytes = (invocation.arguments.first() as String).toByteArray()
            ByteBuffer.wrap(bytes)
        }
        `when`(deserialize(anyOrNull(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            val bytes = invocation.arguments.first() as ByteBuffer
            UTF_8.decode(bytes).toString()
        }
    }
    private val offsetTrackersManager = mock(OffsetTrackersManager::class.java).apply {
        `when`(getNextOffset(anyOrNull(), anyInt())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val partition = invocation.arguments[1] as Int
            offsetsPerTopicPartition[topic]!![partition]!!.getAndIncrement()
        }
        `when`(offsetReleased(anyOrNull(), anyInt(), anyLong())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val partition = invocation.arguments[1] as Int
            val offset = invocation.arguments[2] as Long
            releasedOffsetsPerTopicPartition[topic]!![partition]!!.add(offset)
        }
        `when`(maxVisibleOffset(anyString(), anyInt())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val partition = invocation.arguments[1] as Int
            dbRecords[topic]!![partition]!!.map { it.offset }.maxOrNull()
        }
    }
    private val partitionAllocator = mock(PartitionAllocator::class.java).apply {
        // allocate all partitions to the registered subscription
        `when`(register(anyString(), anyOrNull())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val listener = invocation.arguments[1] as PartitionAllocationListener
            listener.onPartitionsAssigned(topic, (1..topicPartitions).toSet())
        }
    }
    private val partitionAssignor = mock(PartitionAssignor::class.java).apply {
        // assign all records to partition 2
        `when`(assign(anyOrNull(), anyInt())).thenReturn(2)
    }

    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }

    private val subscriptionConfig = SubscriptionConfig("consumer-group-1", topic1)

    @Test
    fun `non-transactional subscription processes records, commits offsets and writes new records successfully`() {
        val topic1Records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        dbRecords[topic1]!![1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(
            subscriptionConfig,
            null,
            processor,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )

        subscription.start()

        val expectedDbRecords = listOf(
            RecordDbEntry(topic2, 2, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(2)
            assertContainsItemsWithLock(dbCommittedOffsets[topic1]!![1]!!, listOf(2))
            assertContainsItemsWithLock(dbRecords[topic2]!![2]!!, expectedDbRecords)
            assertContainsItemsWithLock(releasedOffsetsPerTopicPartition[topic2]!![2]!!, listOf(1, 2))
        }

        subscription.stop()
    }

    @Test
    fun `transactional subscription processes records, commits offsets and writes new records successfully`() {
        val topic1Records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        dbRecords[topic1]!![1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription =
            DBEventLogSubscription(
                subscriptionConfig,
                1,
                processor,
                null,
                avroSchemaRegistry,
                offsetTrackersManager,
                partitionAllocator,
                partitionAssignor,
                dbAccessProvider,
                lifecycleCoordinatorFactory,
                pollingTimeout
            )

        subscription.start()

        val expectedDbRecords = listOf(
            RecordDbEntry(topic2, 2, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(2)
            assertContainsItemsWithLock(dbCommittedOffsets[topic1]!![1]!!, listOf(2))
            assertContainsItemsWithLock(dbRecords[topic2]!![2]!!, expectedDbRecords)
            assertContainsItemsWithLock(releasedOffsetsPerTopicPartition[topic2]!![2]!!, listOf(1, 2))
        }

        subscription.stop()
    }

    @Test
    fun `subscription processes records with null values, commits offsets and writes new records successfully`() {
        val topic1Records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), null),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), null)
        )
        dbRecords[topic1]!![1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(
            subscriptionConfig,
            1,
            processor,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )

        subscription.start()

        val expectedDbRecords = listOf(
            RecordDbEntry(topic2, 2, 1, "key-1".toByteArray(), null),
            RecordDbEntry(topic2, 2, 2, "key-2".toByteArray(), null)
        )
        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(2)
            assertContainsItemsWithLock(dbCommittedOffsets[topic1]!![1]!!, listOf(2))
            assertContainsItemsWithLock(dbRecords[topic2]!![2]!!, expectedDbRecords)
            assertContainsItemsWithLock(releasedOffsetsPerTopicPartition[topic2]!![2]!!, listOf(1, 2))
        }

        subscription.stop()
    }

    @Test
    @Suppress("MaxLineLength")
    fun `if transactional db write fails temporarily, transactional subscription delivers records to processor multiple times`() {
        failuresToSimulateForAtomicDbWrite = 3
        val topic1Records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        val expectedRecordsToProcess = topic1Records.size * (failuresToSimulateForAtomicDbWrite + 1)
        dbRecords[topic1]!![1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(
            subscriptionConfig,
            1,
            processor,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )

        subscription.start()

        val expectedDbRecords = listOf(
            RecordDbEntry(topic2, 2, 7, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 8, "key-2".toByteArray(), "value-2".toByteArray())
        )
        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(expectedRecordsToProcess)
            assertContainsItemsWithLock(dbCommittedOffsets[topic1]!![1]!!, listOf(2))
            assertContainsItemsWithLock(dbRecords[topic2]!![2]!!, expectedDbRecords)
            assertContainsItemsWithLock(releasedOffsetsPerTopicPartition[topic2]!![2]!!, (1L..8L).toList())
        }

        subscription.stop()
    }

    @Test
    @Suppress("MaxLineLength")
    fun `if writing of records to db fails fails temporarily, non-transactional subscription delivers records to processor multiple times`() {
        failuresToSimulateForDbRecordsWrite = 3
        val topic1Records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        val expectedRecordsToProcess = topic1Records.size * (failuresToSimulateForDbRecordsWrite + 1)
        dbRecords[topic1]!![1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(
            subscriptionConfig,
            null,
            processor,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )

        subscription.start()

        val expectedDbRecords = listOf(
            RecordDbEntry(topic2, 2, 7, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 8, "key-2".toByteArray(), "value-2".toByteArray())
        )
        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(expectedRecordsToProcess)
            assertContainsItemsWithLock(dbCommittedOffsets[topic1]!![1]!!, listOf(2))
            assertContainsItemsWithLock(dbRecords[topic2]!![2]!!, expectedDbRecords)
            assertContainsItemsWithLock(releasedOffsetsPerTopicPartition[topic2]!![2]!!, (1L..8L).toList())
        }

        subscription.stop()
    }

    @Test
    @Suppress("MaxLineLength")
    fun `if writing of offsets to db fails fails temporarily, non-transactional subscription produces records and delivers them to processor multiple times`() {
        failuresToSimulateForDbOffsetWrite = 3
        val topic1Records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        val expectedRecordsToProcess = topic1Records.size * (failuresToSimulateForDbOffsetWrite + 1)
        dbRecords[topic1]!![1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(
            subscriptionConfig,
            null,
            processor,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )

        subscription.start()

        val expectedDbRecords = listOf(
            RecordDbEntry(topic2, 2, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic2, 2, 3, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 4, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic2, 2, 5, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 6, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic2, 2, 7, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic2, 2, 8, "key-2".toByteArray(), "value-2".toByteArray())
        )
        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(expectedRecordsToProcess)
            assertContainsItemsWithLock(dbCommittedOffsets[topic1]!![1]!!, listOf(2))
            assertContainsItemsWithLock(dbRecords[topic2]!![2]!!, expectedDbRecords)
            assertContainsItemsWithLock(releasedOffsetsPerTopicPartition[topic2]!![2]!!, (1L..8L).toList())
        }

        subscription.stop()
    }

    @Test
    fun `partition assignment listener is invoked when partitions are unassigned`() {
        val partitionListener = mock(PartitionAssignmentListener::class.java)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic1)
        val subscription = DBEventLogSubscription(
            subscriptionConfig,
            null,
            processor,
            partitionListener,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )

        subscription.start()
        verify(partitionListener, times(1)).onPartitionsAssigned(listOf(topic1 to 1, topic1 to 2))
        verify(partitionListener, never()).onPartitionsUnassigned(anyList())
    }

    class InMemoryProcessor<K : Any, V : Any>(
        private val processedRecords: MutableList<EventLogRecord<K, V>>,
        override val keyClass: Class<K>,
        override val valueClass: Class<V>,
        private val topicToWriteTo: String
    ) : EventLogProcessor<K, V> {

        override fun onNext(events: List<EventLogRecord<K, V>>): List<Record<*, *>> {
            processedRecords.addAll(events)
            return events.map { Record(topicToWriteTo, it.key, it.value) }
        }
    }

    fun <E> assertContainsItemsWithLock(actual: MutableList<E>, expected: List<E>) {
        synchronized(actual) {
            assertThat(actual).containsExactlyElementsOf(expected)
        }
    }

}
