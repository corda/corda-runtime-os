package net.corda.messaging.db.publisher

import com.nhaarman.mockito_kotlin.anyOrNull
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.RecordDbEntry
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import java.nio.ByteBuffer
import java.sql.SQLNonTransientException
import java.sql.SQLTransientException
import java.util.Collections
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicLong

class DBPublisherTest {

    private val writtenDbRecords = Collections.synchronizedList(ArrayList<RecordDbEntry>())
    private val offset = AtomicLong(1)
    private val releasedOffsets = mutableListOf<Long>()

    private var failureToSimulateForDbWrite: Exception? = null
    private val dbAccessProvider = mock(DBAccessProvider::class.java).apply {
        @Suppress("UNCHECKED_CAST")
        `when`(writeRecords(anyList(), anyOrNull())).thenAnswer { invocation ->
            val records = invocation.arguments[0] as List<RecordDbEntry>
            val postTxFn = invocation.arguments[1] as ((records: List<RecordDbEntry>) -> Unit)

            try {
                if (failureToSimulateForDbWrite != null) {
                    throw failureToSimulateForDbWrite!!
                }

                writtenDbRecords.addAll(records)
            } finally {
                postTxFn(records)
            }
        }
    }
    private val avroSchemaRegistry = mock(AvroSchemaRegistry::class.java).apply {
        `when`(serialize(anyOrNull())).thenAnswer { invocation ->
            val bytes = (invocation.arguments.first() as String).toByteArray()
            ByteBuffer.wrap(bytes)
        }
    }
    private val offsetTrackersManager = mock(OffsetTrackersManager::class.java).apply {
        `when`(getNextOffset(anyOrNull())).thenAnswer { offset.getAndIncrement() }
        `when`(offsetReleased(anyOrNull(), anyLong())).thenAnswer { releasedOffsets.add(it.arguments[1] as Long) }
    }

    private val transactionalConfig = PublisherConfig("client-id", 1)
    private val nonTransactionalConfig = PublisherConfig("client-id")

    private val topic = "test.topic"

    @Test
    fun `transactional publisher writes records and releases offsets successfully`() {
        val dbPublisher = DBPublisher(transactionalConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager)
        dbPublisher.start()
        val records = listOf(
            Record(topic, "key1", "value1"),
            Record(topic, "key2", "value2")
        )

        dbPublisher.use {  publisher ->
            val results = publisher.publish(records)
            assertThat(results).hasSize(1)
            results.first().get()

            assertThat(writtenDbRecords).hasSize(2)
            val writtenRecords = writtenDbRecords.map { Record(it.topic, String(it.key), String(it.value!!)) }
            assertThat(writtenRecords).containsExactlyInAnyOrderElementsOf(records)
            assertThat(releasedOffsets).containsExactlyInAnyOrder(1, 2)
        }
    }

    @Test
    fun `non-transactional publisher writes records and releases offsets successfully`() {
        val dbPublisher = DBPublisher(nonTransactionalConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager)
        dbPublisher.start()
        val records = listOf(
            Record(topic, "key1", "value1"),
            Record(topic, "key2", "value2")
        )

        dbPublisher.use { publisher ->
            val results = publisher.publish(records)
            assertThat(results).hasSize(records.size)
            results.forEach { it.get() }

            assertThat(writtenDbRecords).hasSize(2)
            val writtenRecords = writtenDbRecords.map { Record(it.topic, String(it.key), String(it.value!!)) }
            assertThat(writtenRecords).containsExactlyInAnyOrderElementsOf(records)
            assertThat(releasedOffsets).containsExactlyInAnyOrder(1, 2)
        }
    }

    @Test
    fun `publisher can write records with explicit partitions successfully`() {
        val dbPublisher = DBPublisher(nonTransactionalConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager)
        dbPublisher.start()
        val records = listOf(
            1 to Record(topic, "key1", "value1"),
            2 to Record(topic, "key2", "value2")
        )

        dbPublisher.use {publisher ->
            val results = publisher.publishToPartition(records)
            assertThat(results).hasSize(records.size)
            results.forEach { it.get() }

            assertThat(writtenDbRecords).hasSize(2)
            val keyValueEntries = writtenDbRecords.map { String(it.key) to Pair(it.partition, String(it.value!!)) }.toMap()
            assertThat(keyValueEntries).containsAllEntriesOf(mapOf(
                "key1" to Pair(1, "value1"),
                "key2" to Pair(2, "value2")
            ))
            assertThat(releasedOffsets).containsExactlyInAnyOrder(1, 2)
        }
    }

    @Test
    fun `publisher can write records with null values successfully`() {
        val dbPublisher = DBPublisher(transactionalConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager)
        dbPublisher.start()
        val records = listOf(
            Record(topic, "key1", null),
            Record(topic, "key2", null)
        )

        dbPublisher.use { publisher ->
            val results = publisher.publish(records)
            assertThat(results).hasSize(1)
            results.first().get()

            assertThat(writtenDbRecords).hasSize(2)
            val keyValueEntries = writtenDbRecords.map { String(it.key) to it.value }.toMap()
            assertThat(keyValueEntries).containsAllEntriesOf(mapOf(
                "key1" to null,
                "key2" to null
            ))
            assertThat(releasedOffsets).containsExactlyInAnyOrder(1, 2)
        }
    }

    @Test
    fun `when db access fails with transient error, the publisher fails the requests with intermittent exception`() {
        failureToSimulateForDbWrite = SQLTransientException()
        val dbPublisher = DBPublisher(transactionalConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager)
        dbPublisher.start()
        val records = listOf(
            Record(topic, "key1", null),
            Record(topic, "key2", null)
        )

        dbPublisher.use { publisher ->
            val results = publisher.publish(records)

            assertThat(results).hasSize(1)
            assertThatThrownBy { results.first().get() }
                .isInstanceOf(ExecutionException::class.java)
                .hasCauseInstanceOf(CordaMessageAPIIntermittentException::class.java)
            assertThat(releasedOffsets).containsExactlyInAnyOrder(1, 2)
            assertThat(writtenDbRecords).isEmpty()
        }
    }

    @Test
    fun `when db access fails with non-transient error, the publisher fails the requests with fatal exception`() {
        failureToSimulateForDbWrite = SQLNonTransientException()
        val dbPublisher = DBPublisher(transactionalConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager)
        dbPublisher.start()
        val records = listOf(
            Record(topic, "key1", null),
            Record(topic, "key2", null)
        )

        dbPublisher.use { publisher ->
            val results = publisher.publish(records)

            assertThat(results).hasSize(1)
            assertThatThrownBy { results.first().get() }
                .isInstanceOf(ExecutionException::class.java)
                .hasCauseInstanceOf(CordaMessageAPIFatalException::class.java)
            assertThat(releasedOffsets).containsExactlyInAnyOrder(1, 2)
            assertThat(writtenDbRecords).isEmpty()
        }
    }

}