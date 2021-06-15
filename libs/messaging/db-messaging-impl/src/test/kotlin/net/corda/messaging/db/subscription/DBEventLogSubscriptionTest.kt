package net.corda.messaging.db.subscription

import com.nhaarman.mockito_kotlin.anyOrNull
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.DbSchema
import net.corda.messaging.db.persistence.RecordDbEntry
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.testing.common.internal.eventually
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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.SQLTransientException
import java.util.concurrent.atomic.AtomicLong

class DBEventLogSubscriptionTest {

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"
    private val dbRecords = mapOf(
        topic1 to mutableListOf<RecordDbEntry>(),
        topic2 to mutableListOf()
    )
    private val dbOffsets = mapOf(
        topic1 to mutableListOf<Long>(),
        topic2 to mutableListOf()
    )
    private val offset = AtomicLong(1)
    private val releasedOffsets = mutableListOf<Long>()

    private val processedRecords = mutableListOf<EventLogRecord<String, String>>()

    private var failuresToSimulateForDbOffsetWrite = 0
    private var failuresToSimulateForDbRecordsWrite = 0
    private var failuresToSimulateForAtomicDbWrite = 0
    private val dbAccessProvider = mock(DBAccessProvider::class.java).apply {
        @Suppress("UNCHECKED_CAST")
        `when`(writeRecords(anyList(), anyOrNull()))
            .thenAnswer { invocation ->
                val records = (invocation.arguments.first() as List<RecordDbEntry>)
                val postTxFn = invocation.arguments[1] as ((records: List<RecordDbEntry>) -> Unit)

                try {
                    if (failuresToSimulateForDbRecordsWrite > 0) {
                        failuresToSimulateForDbRecordsWrite--
                        throw SQLTransientException()
                    }

                    records.forEach { dbRecords[it.topic]!!.add(it) }
                } finally {
                    postTxFn(records)
                }

            }
        `when`(readRecords(anyString(), anyLong(), anyLong(), anyInt()))
            .thenAnswer { invocation ->
                val topic = invocation.arguments[0] as String
                val startOffset = invocation.arguments[1] as Long
                val maxOffset = invocation.arguments[2] as Long
                val limit = invocation.arguments[3] as Int
                dbRecords[topic]!!.filter { it.offset in startOffset until maxOffset+1 }.take(limit)
            }
        `when`(writeOffset(anyString(), anyString(), anyLong()))
            .thenAnswer { invocation ->
                if (failuresToSimulateForDbOffsetWrite > 0) {
                    failuresToSimulateForDbOffsetWrite--
                    throw SQLTransientException()
                }

                val topic = invocation.arguments[0] as String
                val offset = invocation.arguments[2] as Long
                dbOffsets[topic]!!.add(offset)
            }
        @Suppress("UNCHECKED_CAST")
        `when`(writeOffsetAndRecordsAtomically(anyString(), anyString(), anyLong(), anyList(), anyOrNull()))
            .thenAnswer { invocation ->
                val topic = invocation.arguments[0] as String
                val consumerGroup = invocation.arguments[1] as String
                val offset = invocation.arguments[2] as Long
                val records = invocation.arguments[3] as List<RecordDbEntry>
                val postTxFn = invocation.arguments[4] as ((records: List<RecordDbEntry>) -> Unit)

                if (failuresToSimulateForAtomicDbWrite > 0) {
                    postTxFn(records)
                    failuresToSimulateForAtomicDbWrite--
                    throw SQLTransientException()
                }

                writeOffset(topic, consumerGroup, offset)
                writeRecords(records, postTxFn)
            }
        `when`(getMaxCommittedOffset(anyString(), anyString()))
            .thenAnswer { invocation ->
                val topic = invocation.arguments[0] as String
                dbOffsets[topic]!!.maxOrNull()
            }
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
        `when`(getNextOffset(anyOrNull())).thenAnswer { offset.getAndIncrement() }
        `when`(offsetReleased(anyOrNull(), anyLong())).thenAnswer { releasedOffsets.add(it.arguments[1] as Long) }
        `when`(maxVisibleOffset(anyString())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            dbRecords[topic]!!.map { it.offset }.maxOrNull()
        }
    }


    private val transactionalConfig = SubscriptionConfig("consumer-group-1", topic1, 1)
    private val nonTransactionalConfig = SubscriptionConfig("consumer-group-1", topic1)

    @Test
    fun `non-transactional subscription processes records, commits offsets and writes new records successfully`() {
        val topic1Records = listOf(
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        dbRecords[topic1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(nonTransactionalConfig, processor, null, avroSchemaRegistry, offsetTrackersManager, dbAccessProvider, pollingTimeout = 5.millis)

        subscription.start()

        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(2)
            assertThat(dbOffsets[topic1]!!.maxOrNull()).isNotNull
            assertThat(dbOffsets[topic1]!!.maxOrNull()!!).isEqualTo(2)
            assertThat(dbRecords[topic2]).containsExactlyElementsOf(listOf(
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray())
            ))
            assertThat(releasedOffsets).containsExactlyElementsOf(listOf(1, 2))
        }

        subscription.stop()
    }

    @Test
    fun `transactional subscription processes records, commits offsets and writes new records successfully`() {
        val topic1Records = listOf(
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        dbRecords[topic1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(transactionalConfig, processor, null, avroSchemaRegistry, offsetTrackersManager, dbAccessProvider, pollingTimeout = 5.millis)

        subscription.start()

        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(2)
            assertThat(dbOffsets[topic1]!!.maxOrNull()).isNotNull
            assertThat(dbOffsets[topic1]!!.maxOrNull()!!).isEqualTo(2)
            assertThat(dbRecords[topic2]).containsExactlyElementsOf(listOf(
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray())
            ))
            assertThat(releasedOffsets).containsExactlyElementsOf(listOf(1, 2))
        }

        subscription.stop()
    }

    @Test
    fun `subscription processes records with null values, commits offsets and writes new records successfully`() {
        val topic1Records = listOf(
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), null),
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), null)
        )
        dbRecords[topic1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(transactionalConfig, processor, null, avroSchemaRegistry, offsetTrackersManager, dbAccessProvider, pollingTimeout = 5.millis)

        subscription.start()

        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(2)
            assertThat(dbOffsets[topic1]!!.maxOrNull()).isNotNull
            assertThat(dbOffsets[topic1]!!.maxOrNull()!!).isEqualTo(2)
            assertThat(dbRecords[topic2]).containsExactlyElementsOf(listOf(
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), null),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), null)
            ))
            assertThat(releasedOffsets).containsExactlyElementsOf(listOf(1, 2))
        }

        subscription.stop()
    }

    @Test
    fun `if transactional db write fails temporarily, transactional subscription delivers records to processor multiple times`() {
        failuresToSimulateForAtomicDbWrite = 3
        val topic1Records = listOf(
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        val expectedRecordsToProcess = topic1Records.size * (failuresToSimulateForAtomicDbWrite + 1)
        dbRecords[topic1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(transactionalConfig, processor, null, avroSchemaRegistry, offsetTrackersManager, dbAccessProvider, pollingTimeout = 5.millis)

        subscription.start()

        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(expectedRecordsToProcess)
            assertThat(dbOffsets[topic1]!!.maxOrNull()).isNotNull
            assertThat(dbOffsets[topic1]!!.maxOrNull()!!).isEqualTo(2)
            assertThat(dbRecords[topic2]).containsExactlyElementsOf(listOf(
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 7, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 8, "key-2".toByteArray(), "value-2".toByteArray())
            ))
            assertThat(releasedOffsets).containsExactlyElementsOf((1L..8L).toList())
        }

        subscription.stop()
    }

    @Test
    fun `if writing of records to db fails fails temporarily, non-transactional subscription delivers records to processor multiple times`() {
        failuresToSimulateForDbRecordsWrite = 3
        val topic1Records = listOf(
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        val expectedRecordsToProcess = topic1Records.size * (failuresToSimulateForDbRecordsWrite + 1)
        dbRecords[topic1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(nonTransactionalConfig, processor, null, avroSchemaRegistry, offsetTrackersManager, dbAccessProvider, pollingTimeout = 5.millis)

        subscription.start()

        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(expectedRecordsToProcess)
            assertThat(dbOffsets[topic1]!!.maxOrNull()).isNotNull
            assertThat(dbOffsets[topic1]!!.maxOrNull()!!).isEqualTo(2)
            assertThat(dbRecords[topic2]).containsExactlyElementsOf(listOf(
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 7, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 8, "key-2".toByteArray(), "value-2".toByteArray())
            ))
            assertThat(releasedOffsets).containsExactlyElementsOf((1L..8L).toList())
        }

        subscription.stop()
    }

    @Test
    fun `if writing of offsets to db fails fails temporarily, non-transactional subscription produces records and delivers them to processor multiple times`() {
        failuresToSimulateForDbOffsetWrite = 3
        val topic1Records = listOf(
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray())
        )
        val expectedRecordsToProcess = topic1Records.size * (failuresToSimulateForDbOffsetWrite + 1)
        dbRecords[topic1]!!.addAll(topic1Records)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic2)
        val subscription = DBEventLogSubscription(nonTransactionalConfig, processor, null, avroSchemaRegistry, offsetTrackersManager, dbAccessProvider, pollingTimeout = 5.millis)

        subscription.start()

        eventually(1.seconds, 5.millis) {
            assertThat(processedRecords.size).isEqualTo(expectedRecordsToProcess)
            assertThat(dbOffsets[topic1]!!.maxOrNull()).isNotNull
            assertThat(dbOffsets[topic1]!!.maxOrNull()!!).isEqualTo(2)
            assertThat(dbRecords[topic2]).containsExactlyElementsOf(listOf(
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 1, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 2, "key-2".toByteArray(), "value-2".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 3, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 4, "key-2".toByteArray(), "value-2".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 5, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 6, "key-2".toByteArray(), "value-2".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 7, "key-1".toByteArray(), "value-1".toByteArray()),
                RecordDbEntry(topic2, DbSchema.FIXED_PARTITION_NO, 8, "key-2".toByteArray(), "value-2".toByteArray())
            ))
            assertThat(releasedOffsets).containsExactlyElementsOf((1L..8L).toList())
        }

        subscription.stop()
    }

    @Test
    fun `partition assignment listener is invoked when partitions are unassigned`() {
        val partitionListener = mock(PartitionAssignmentListener::class.java)
        val processor = InMemoryProcessor(processedRecords, String::class.java, String::class.java, topic1)
        val subscription = DBEventLogSubscription(nonTransactionalConfig, processor, partitionListener, avroSchemaRegistry, offsetTrackersManager, dbAccessProvider, pollingTimeout = 5.millis)

        subscription.start()
        verify(partitionListener, times(1)).onPartitionsAssigned(listOf(topic1 to 1))
        verify(partitionListener, never()).onPartitionsUnassigned(anyList())

        subscription.stop()
        verify(partitionListener, times(1)).onPartitionsUnassigned(listOf(topic1 to 1))
    }

    class InMemoryProcessor<K: Any, V: Any>(private val processedRecords: MutableList<EventLogRecord<K, V>>,
                                            override val keyClass: Class<K>,
                                            override val valueClass: Class<V>,
                                            private val topicToWriteTo: String): EventLogProcessor<K, V> {

        override fun onNext(events: List<EventLogRecord<K, V>>): List<Record<*, *>> {
            processedRecords.addAll(events)
            return events.map { Record(topicToWriteTo, it.key, it.value) }
        }
    }

}